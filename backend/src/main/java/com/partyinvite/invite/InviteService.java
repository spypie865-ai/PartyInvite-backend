package com.partyinvite.invite;

import com.partyinvite.invite.InviteDtos.*;
import com.partyinvite.mail.MailService;
import com.razorpay.Order;
import com.razorpay.RazorpayClient;
import com.razorpay.Utils;
import jakarta.transaction.Transactional;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Service
public class InviteService {
    private final InviteRepository repository;
    private final MailService mail;
    @Value("${app.frontend-url}") private String frontendUrl;
    @Value("${razorpay.key-id}") private String keyId;
    @Value("${razorpay.key-secret}") private String keySecret;

    public InviteService(InviteRepository repository, MailService mail) {
        this.repository = repository; this.mail = mail;
    }

    @Transactional
    public Created create(CreateRequest req, MultipartFile photo) {
        if (req.locations().stream().map(String::trim).distinct().count() != req.locations().size())
            throw bad("Location options must be unique.");
        if (req.amountRupees() == null || req.amountRupees() < 0 || req.amountRupees() > 50000)
            throw bad("Amount must be between ₹0 and ₹50,000.");
        String photoPath = null;
        if (photo != null && !photo.isEmpty()) {
            String type = photo.getContentType();
            if (photo.getSize() > 2 * 1024 * 1024) throw bad("Photo must be 2 MB or smaller.");
            if (!Set.of("image/jpeg","image/png","image/webp").contains(type)) throw bad("Photo must be JPG, PNG or WEBP.");
            try {
                Path dir = Path.of("uploads"); Files.createDirectories(dir);
                String ext = type.equals("image/jpeg") ? ".jpg" : type.equals("image/png") ? ".png" : ".webp";
                photoPath = dir.resolve(UUID.randomUUID() + ext).toAbsolutePath().toString();
                photo.transferTo(Path.of(photoPath));
            } catch (Exception e) { throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not save photo."); }
        }
        Invite i = new Invite();
        i.setId(UUID.randomUUID().toString()); i.setEventName(req.eventName().trim()); i.setMessage(req.message().trim());
        i.setAmountPaise(Math.round(req.amountRupees() * 100)); i.setHostName(req.hostName().trim());
        i.setHostEmail(req.hostEmail().trim()); i.setGuestName(req.guestName().trim()); i.setGuestEmail(req.guestEmail().trim());
        i.setLocations(req.locations().stream().map(String::trim).toList()); i.setPhotoFile(photoPath);
        i.setCreatedAt(Instant.now()); i.setExpiresAt(Instant.now().plus(Duration.ofHours(req.validityHours())));
        repository.save(i); mail.inviteGuest(i);
        return new Created(i.getId(), i.getExpiresAt(), frontendUrl + "/i/" + i.getId());
    }

    @Transactional
    public PublicInvite getPublic(String id) {
        Invite i = get(id); refreshExpiry(i);
        return new PublicInvite(i.getId(), i.getEventName(), i.getMessage(), i.getGuestName(),
            i.getHostName(), i.getPhotoFile()!=null, i.getStatus(), i.getExpiresAt(), i.getChosenLocation());
    }

    public byte[] photo(String id) {
        Invite i = get(id); refreshExpiry(i);
        if (i.getPhotoFile()==null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No photo for this invite.");
        try { return Files.readAllBytes(Path.of(i.getPhotoFile())); }
        catch (Exception e) { throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Photo not found."); }
    }

    @Transactional
    public AcceptResponse accept(String id) {
        Invite i = get(id); checkAvailable(i);
        if (i.getStatus()==Invite.Status.PENDING) {
            i.setStatus(Invite.Status.ACCEPTED);
            if (!i.isAcceptanceEmailSent()) { i.setAcceptanceEmailSent(true); mail.accepted(i); }
        }
        return new AcceptResponse(i.getLocations(), i.getAmountPaise()>0, i.getAmountPaise(),
            i.getEventName(), i.getGuestName(), i.getGuestEmail(), i.getChosenLocation());
    }

    @Transactional
    public Map<String,Object> chooseLocation(String id, LocationRequest req) {
        Invite i = get(id); checkAvailable(i);
        if (i.getStatus()==Invite.Status.PENDING) throw bad("Press YES before choosing a location.");
        if (i.getStatus()==Invite.Status.CONFIRMED || i.getStatus()==Invite.Status.PAID)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This invite is already completed.");
        if (!i.getLocations().contains(req.location())) throw bad("Choose one of the host's location options.");
        i.setChosenLocation(req.location());
        if (i.getAmountPaise()==0) {
            i.setStatus(Invite.Status.CONFIRMED); mail.confirmed(i);
            return Map.of("completed", true, "chosenLocation", i.getChosenLocation());
        }
        return Map.of("completed", false, "chosenLocation", i.getChosenLocation(),
            "amountPaise", i.getAmountPaise(), "eventName", i.getEventName());
    }

    @Transactional
    public OrderResponse createOrder(String id) {
        Invite i = get(id); checkAvailable(i);
        if (i.getStatus()==Invite.Status.CONFIRMED || i.getStatus()==Invite.Status.PAID)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This invite is already completed.");
        if (i.getStatus()!=Invite.Status.ACCEPTED || i.getChosenLocation()==null || i.getAmountPaise()<=0)
            throw bad("Accept the invite and choose a location before payment.");
        if (keyId.isBlank() || keySecret.isBlank())
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Payment gateway is not configured.");
        try {
            RazorpayClient client = new RazorpayClient(keyId, keySecret);
            JSONObject options = new JSONObject();
            options.put("amount", i.getAmountPaise()); options.put("currency", "INR"); options.put("receipt", i.getId());
            Order order = client.orders.create(options); i.setOrderId(order.get("id"));
            return new OrderResponse(keyId, order.get("id"), i.getAmountPaise(), "INR", i.getGuestName(), i.getGuestEmail());
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Could not create payment order.");
        }
    }

    @Transactional
    public void verify(String id, VerifyRequest req) {
        Invite i = get(id); checkAvailable(i);
        if (i.getStatus()==Invite.Status.PAID || i.getStatus()==Invite.Status.CONFIRMED)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This invite is already completed.");
        if (i.getOrderId()==null || !i.getOrderId().equals(req.razorpay_order_id()))
            throw bad("Payment order does not match this invite.");
        try {
            String payload = req.razorpay_order_id() + "|" + req.razorpay_payment_id();
            String expected = Utils.getHash(payload, keySecret);
            if (!MessageDigest.isEqual(expected.getBytes(), req.razorpay_signature().getBytes()))
                throw bad("Payment signature is invalid.");
        } catch (ResponseStatusException e) { throw e; }
        catch (Exception e) { throw bad("Could not verify payment signature."); }
        i.setPaymentId(req.razorpay_payment_id()); i.setStatus(Invite.Status.PAID); mail.paid(i);
    }

    private Invite get(String id) {
        return repository.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invite not found."));
    }
    private void refreshExpiry(Invite i) {
        if (Instant.now().isAfter(i.getExpiresAt()) && i.getStatus()!=Invite.Status.CONFIRMED && i.getStatus()!=Invite.Status.PAID)
            i.setStatus(Invite.Status.EXPIRED);
    }
    private void checkAvailable(Invite i) {
        refreshExpiry(i);
        if (i.getStatus()==Invite.Status.EXPIRED)
            throw new ResponseStatusException(HttpStatus.GONE, "This invite has expired.");
        if (i.getStatus()==Invite.Status.CONFIRMED || i.getStatus()==Invite.Status.PAID)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This invite is already completed.");
    }
    private ResponseStatusException bad(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
}
