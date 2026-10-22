package com.partyinvite.mail;

import com.partyinvite.invite.Invite;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

import java.text.NumberFormat;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Service
public class MailService {
    private static final Logger log = LoggerFactory.getLogger(MailService.class);
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final DateTimeFormatter WHEN =
            DateTimeFormatter.ofPattern("EEE, d MMM yyyy 'at' h:mm a", Locale.ENGLISH);

    private final JavaMailSender sender;
    @Value("${app.frontend-url}") private String frontendUrl;

    public MailService(JavaMailSender sender) { this.sender = sender; }

    // ───────────────────────── Public API ─────────────────────────

    /** Guest receives the invitation. */
    public void inviteGuest(Invite i) {
        String link = frontendUrl + "/i/" + i.getId();
        String expiry = "This invitation vanishes on " + when(i.getExpiresAt()) + ". Don't ghost us. 👻";

        String body =
             quote(i.getMessage(), i.getHostName())
          + p("Quick warning: there's a big <b style=\"color:#16a34a\">YES</b> button and a tiny "
              + "<b style=\"color:#dc2626\">NO</b> button. Only one of them can be caught. 😏")
          + small(expiry);

        String text = "Hey " + i.getGuestName() + "! 🎉\n\n"
            + i.getHostName() + " invited you to " + i.getEventName() + ".\n\n"
            + "\"" + i.getMessage() + "\"\n\n"
            + "Open your invitation: " + link + "\n\n"
            + expiry;

        send(i.getGuestEmail(), "🎉 " + i.getHostName() + " has a plan for you: " + i.getEventName(),
             text,
             layout("You've been summoned. Resistance is futile.", "🎉",
                    "You're invited, " + e(i.getGuestName()) + "!", body,
                    "Open my invitation 👀", link));
    }

    /** Host learns the guest said YES. */
    public void accepted(Invite i) {
        String body =
            p("<b>" + e(i.getGuestName()) + "</b> just smashed the YES button for <b>"
              + e(i.getEventName()) + "</b>. The NO button never stood a chance.")
          + p("Next up: they're picking the location. Sit tight and look casual. 😎");

        String text = i.getGuestName() + " said YES to " + i.getEventName() + "! 🥳\n"
            + "They're picking the location now.";

        send(i.getHostEmail(), "🥳 " + i.getGuestName() + " said YES!", text,
             layout("The NO button never stood a chance.", "🥳",
                    e(i.getGuestName()) + " said YES!", body, null, null));
    }

    /** Host and guest both learn the final location. */
    public void confirmed(Invite i) {
        String place = e(i.getChosenLocation());

        String hostBody =
            p("<b>" + e(i.getGuestName()) + "</b> has chosen the spot for <b>"
              + e(i.getEventName()) + "</b>:")
          + highlight("📍 " + place)
          + p("Time to start pretending you planned this all along.");
        String hostText = i.getGuestName() + " chose " + i.getChosenLocation()
            + " for " + i.getEventName() + ". 📍";

        send(i.getHostEmail(), "📍 Location locked: " + i.getEventName(), hostText,
             layout("The venue has been decided.", "📍", "Location locked in!", hostBody, null, null));

        String guestBody =
            p("You picked, and it's official. See you at:")
          + highlight("📍 " + place)
          + p("Wear something nice. Or something funny. We're not the dress code police. 👕");
        String guestText = "You're all set! 🎊\nYour location for " + i.getEventName()
            + " is " + i.getChosenLocation() + ".";

        send(i.getGuestEmail(), "🎊 You're all set for " + i.getEventName(), guestText,
             layout("It's official. No take-backs.", "🎊", "You're all set!", guestBody, null, null));
    }

    /** Host gets a payment alert, guest gets a receipt. */
    public void paid(Invite i) {
        String amount = rupees(i.getAmountPaise());

        String table = receipt(
            row("Event", e(i.getEventName()))
          + row("Location", e(i.getChosenLocation()))
          + row("Amount", "<b style=\"color:#16a34a\">" + amount + "</b>")
          + row("Payment ID", "<code>" + e(i.getPaymentId()) + "</code>"));

        String hostBody =
            p("<b>" + e(i.getGuestName()) + "</b> just paid up for <b>" + e(i.getEventName())
              + "</b>. Money talks, and it said yes. 💸") + table;
        String hostText = i.getGuestName() + " paid " + amount + ".\nLocation: " + i.getChosenLocation()
            + "\nPayment ID: " + i.getPaymentId();

        send(i.getHostEmail(), "💸 Cha-ching! " + i.getGuestName() + " paid " + amount, hostText,
             layout(amount + " received.", "💸", "Payment received!", hostBody, null, null));

        String guestBody =
            p("Thanks for chipping in. Here's your receipt for <b>" + e(i.getEventName()) + "</b>:")
          + table
          + small("Keep this email, it's your proof that you're officially cool. 😎");
        String guestText = "Payment received for " + i.getEventName() + " 🧾\nAmount: " + amount
            + "\nLocation: " + i.getChosenLocation() + "\nPayment ID: " + i.getPaymentId();

        send(i.getGuestEmail(), "🧾 Your PartyInvite receipt", guestText,
             layout("Payment confirmed. You're officially in.", "🧾", "Your receipt", guestBody, null, null));
    }

    // ───────────────────────── Sending ─────────────────────────

    private void send(String to, String subject, String text, String html) {
        if (to == null || to.isBlank()) return;
        try {
            MimeMessage m = sender.createMimeMessage();
            MimeMessageHelper h = new MimeMessageHelper(m, true, "UTF-8");
            h.setTo(to);
            h.setSubject(subject);
            h.setText(text, html); // plain-text + HTML alternative
            sender.send(m);
            log.info("sended mail to "+to);
        } catch (Exception ex) {
            // Email is best-effort: never roll back a valid invite/acceptance/payment.
            log.warn("Email delivery failed for {}: {}", to, ex.getMessage());
        }
    }

    // ───────────────────────── HTML building blocks ─────────────────────────

    private String layout(String preheader, String emoji, String title, String bodyHtml,
                          String ctaLabel, String ctaUrl) {
        String cta = (ctaLabel == null) ? "" :
            "<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" style=\"margin:28px auto 8px\"><tr>"
          + "<td style=\"border-radius:999px;background:linear-gradient(135deg,#7c3aed,#ec4899);background-color:#7c3aed\">"
          + "<a href=\"" + e(ctaUrl) + "\" style=\"display:inline-block;padding:16px 36px;color:#ffffff;"
          + "font-size:17px;font-weight:700;text-decoration:none;border-radius:999px\">" + ctaLabel + "</a>"
          + "</td></tr></table>"
          + "<p style=\"text-align:center;font-size:12px;color:#9ca3af;margin:8px 0 0\">"
          + "Button not working? Paste this link in your browser:<br>"
          + "<a href=\"" + e(ctaUrl) + "\" style=\"color:#7c3aed;word-break:break-all\">" + e(ctaUrl) + "</a></p>";

        return "<!DOCTYPE html><html><head><meta charset=\"UTF-8\">"
          + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"></head>"
          + "<body style=\"margin:0;padding:0;background:#f3e8ff;font-family:'Segoe UI',Arial,Helvetica,sans-serif\">"
          + "<span style=\"display:none;max-height:0;overflow:hidden;opacity:0;color:transparent\">"
          + e(preheader) + "</span>"
          + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"background:#f3e8ff;padding:24px 12px\"><tr><td align=\"center\">"
          + "<table role=\"presentation\" width=\"560\" cellpadding=\"0\" cellspacing=\"0\" "
          + "style=\"max-width:560px;width:100%;background:#ffffff;border-radius:20px;overflow:hidden;"
          + "box-shadow:0 8px 30px rgba(124,58,237,.18)\">"
          // header
          + "<tr><td align=\"center\" style=\"background:linear-gradient(135deg,#7c3aed,#ec4899);background-color:#7c3aed;padding:36px 24px\">"
          + "<div style=\"font-size:56px;line-height:1\">" + emoji + "</div>"
          + "<h1 style=\"margin:12px 0 0;color:#ffffff;font-size:26px;line-height:1.25\">" + title + "</h1>"
          + "</td></tr>"
          // body
          + "<tr><td style=\"padding:28px 32px 32px;color:#1f2937;font-size:16px;line-height:1.6\">"
          + bodyHtml + cta + "</td></tr>"
          // footer
          + "<tr><td align=\"center\" style=\"background:#faf5ff;padding:18px 24px;font-size:12px;color:#9ca3af\">"
          + "Sent with 💜 and questionable humor by <b style=\"color:#7c3aed\">PartyInvite</b>"
          + "</td></tr>"
          + "</table></td></tr></table></body></html>";
    }

    private String p(String html) { return "<p style=\"margin:0 0 16px\">" + html + "</p>"; }

    private String small(String text) {
        return "<p style=\"margin:20px 0 0;font-size:13px;color:#6b7280\">" + e(text) + "</p>";
    }

    private String quote(String message, String author) {
        if (message == null || message.isBlank()) return "";
        return "<div style=\"margin:20px 0;padding:16px 20px;background:#faf5ff;border-left:5px solid #ec4899;"
             + "border-radius:8px;font-style:italic\">" + e(message).replace("\n", "<br>")
             + "<div style=\"margin-top:8px;font-style:normal;font-size:13px;color:#7c3aed\">— "
             + e(author) + "</div></div>";
    }

    private String highlight(String html) {
        return "<div style=\"margin:20px 0;padding:18px;text-align:center;background:#f0fdf4;border:2px dashed #22c55e;"
             + "border-radius:12px;font-size:20px;font-weight:700;color:#166534\">" + html + "</div>";
    }

    private String receipt(String rows) {
        return "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" "
             + "style=\"margin:16px 0;border:1px solid #e5e7eb;border-radius:12px;border-collapse:separate\">"
             + rows + "</table>";
    }

    private String row(String label, String valueHtml) {
        return "<tr><td style=\"padding:12px 16px;color:#6b7280;font-size:14px;border-bottom:1px solid #f3f4f6\">"
             + label + "</td><td align=\"right\" style=\"padding:12px 16px;font-size:15px;border-bottom:1px solid #f3f4f6\">"
             + valueHtml + "</td></tr>";
    }

    // ───────────────────────── Helpers ─────────────────────────

    /** HTML-escape anything user-supplied (names, messages, locations). */
    private static String e(Object o) { return o == null ? "" : HtmlUtils.htmlEscape(String.valueOf(o)); }

    private static String rupees(long paise) {
        NumberFormat nf = NumberFormat.getNumberInstance(new Locale("en", "IN"));
        nf.setMinimumFractionDigits(2);
        nf.setMaximumFractionDigits(2);
        return "₹" + nf.format(paise / 100.0);
    }

    private static String when(Object t) {
        if (t instanceof Instant in) return in.atZone(IST).format(WHEN) + " IST";
        if (t instanceof LocalDateTime ldt) return ldt.format(WHEN);
        return String.valueOf(t);
    }
}