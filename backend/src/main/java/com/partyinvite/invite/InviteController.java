package com.partyinvite.invite;

import com.partyinvite.invite.InviteDtos.*;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestController
@RequestMapping("/api/invites")
@Validated
public class InviteController {
    private final InviteService service;
    public InviteController(InviteService service) { this.service = service; }

    @PostMapping(consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public Created create(@RequestPart("invite") @Valid CreateRequest request,
                          @RequestPart(value="photo", required=false) MultipartFile photo) {
        return service.create(request, photo);
    }
    @GetMapping("/{id}") public PublicInvite get(@PathVariable String id) { return service.getPublic(id); }
    @GetMapping("/{id}/photo")
    public ResponseEntity<byte[]> photo(@PathVariable String id) {
        byte[] bytes = service.photo(id);
        MediaType type = bytes.length > 0 ? MediaType.APPLICATION_OCTET_STREAM : MediaType.IMAGE_JPEG;
        return ResponseEntity.ok().contentType(type).cacheControl(CacheControl.noCache()).body(bytes);
    }
    @PostMapping("/{id}/accept") public AcceptResponse accept(@PathVariable String id) { return service.accept(id); }
    @PostMapping("/{id}/location") public Map<String,Object> location(@PathVariable String id, @Valid @RequestBody LocationRequest req) {
        return service.chooseLocation(id, req);
    }
    @PostMapping("/{id}/order") public OrderResponse order(@PathVariable String id) { return service.createOrder(id); }
    @PostMapping("/{id}/verify") public ResponseEntity<?> verify(@PathVariable String id, @Valid @RequestBody VerifyRequest req) {
        service.verify(id, req); return ResponseEntity.ok(Map.of("status", "PAID"));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorBody> error(ResponseStatusException ex) {
        return ResponseEntity.status(ex.getStatusCode()).body(new ErrorBody(
            ex.getReason()==null ? "Request failed." : ex.getReason()));
    }
}
