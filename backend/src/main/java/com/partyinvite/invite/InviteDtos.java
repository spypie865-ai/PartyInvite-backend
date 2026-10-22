package com.partyinvite.invite;

import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;

public final class InviteDtos {
    private InviteDtos() {}

    public record CreateRequest(
        @NotBlank @Size(max=100) String eventName,
        @NotBlank @Size(max=1000) String message,
        @NotEmpty @Size(min=1,max=5) List<@NotBlank @Size(max=160) String> locations,
        @NotNull @DecimalMin("0.0") @DecimalMax("50000.0") Double amountRupees,
        @NotBlank @Size(max=100) String hostName,
        @NotBlank @Email @Size(max=254) String hostEmail,
        @NotBlank @Size(max=100) String guestName,
        @NotBlank @Email @Size(max=254) String guestEmail,
        @NotNull @Min(24) @Max(168) Integer validityHours
    ) {}
    public record Created(String id, Instant expiresAt, String link) {}
    public record PublicInvite(String id, String eventName, String message, String guestName,
        String hostName, boolean hasPhoto, Invite.Status status, Instant expiresAt, String chosenLocation) {}
    public record AcceptResponse(List<String> locations, boolean paymentRequired, long amountPaise,
        String eventName, String guestName, String guestEmail, String chosenLocation) {}
    public record LocationRequest(@NotBlank @Size(max=160) String location) {}
    public record OrderResponse(String keyId, String orderId, long amount, String currency,
        String name, String email) {}
    public record VerifyRequest(@NotBlank String razorpay_order_id, @NotBlank String razorpay_payment_id,
        @NotBlank String razorpay_signature) {}
    public record ErrorBody(String message) {}
}
