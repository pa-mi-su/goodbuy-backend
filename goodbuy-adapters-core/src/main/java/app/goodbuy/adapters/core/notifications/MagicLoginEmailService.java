package app.goodbuy.adapters.core.notifications;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.model.Body;
import software.amazon.awssdk.services.ses.model.Content;
import software.amazon.awssdk.services.ses.model.Destination;
import software.amazon.awssdk.services.ses.model.Message;
import software.amazon.awssdk.services.ses.model.SendEmailRequest;
import software.amazon.awssdk.services.ses.model.SesException;

/**
 * Sends real magic-login emails via AWS SES.
 *
 * No stubs. This is production-capable as long as:
 *  - AWS credentials are configured for the running environment
 *  - The sender address is verified in SES (and domain if in production/sandbox)
 */
@Service
public class MagicLoginEmailService {

    private static final Logger log = LoggerFactory.getLogger(MagicLoginEmailService.class);

    private final SesClient sesClient;
    private final String senderEmail;
    private final String appName;

    public MagicLoginEmailService(
            SesClient sesClient,
            @Value("${goodbuy.auth.magic-login.sender}") String senderEmail,
            @Value("${goodbuy.auth.magic-login.app-name:GoodBuy}") String appName
    ) {
        this.sesClient = sesClient;
        this.senderEmail = senderEmail;
        this.appName = appName;
    }

    /**
     * Send the "magic login" email with a one-time link.
     *
     * @param recipientEmail The user's email address.
     * @param magicLoginUrl  The one-time login URL (front-end or backend route).
     */
    public void sendMagicLoginEmail(String recipientEmail, String magicLoginUrl) {
        String subjectText = appName + " – Sign in securely";
        String textBody = buildTextBody(magicLoginUrl);
        String htmlBody = buildHtmlBody(magicLoginUrl);

        Destination destination = Destination.builder()
                .toAddresses(recipientEmail)
                .build();

        Message message = Message.builder()
                .subject(Content.builder().data(subjectText).charset("UTF-8").build())
                .body(Body.builder()
                        .text(Content.builder().data(textBody).charset("UTF-8").build())
                        .html(Content.builder().data(htmlBody).charset("UTF-8").build())
                        .build())
                .build();

        SendEmailRequest request = SendEmailRequest.builder()
                .source(senderEmail)
                .destination(destination)
                .message(message)
                .build();

        try {
            sesClient.sendEmail(request);
            log.info("MagicLoginEmailService: sent magic login email to {}", recipientEmail);
        } catch (SesException ex) {
            // Log cleanly, but don't leak internal AWS details to clients.
            log.error("MagicLoginEmailService: failed to send magic login email to {}: {}",
                    recipientEmail, ex.awsErrorDetails() != null ? ex.awsErrorDetails().errorMessage() : ex.getMessage(), ex);

            // Up to you: either swallow & let caller decide, or throw a 500-ish error.
            // For now, we bubble as a 500-compatible RuntimeException.
            throw new IllegalStateException("Failed to send magic login email", ex);
        }
    }

    private String buildTextBody(String magicLoginUrl) {
        return """
                Here’s your secure sign-in link for %s.

                Tap the link below on your iPhone to continue:

                %s

                This link only works once and expires soon. If you didn’t request this, you can safely ignore this email.

                – The %s team
                """.formatted(appName, magicLoginUrl, appName);
    }

    private String buildHtmlBody(String magicLoginUrl) {
        // Very simple HTML; you can pretty this up later.
        return """
                <html>
                  <body style="font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif; line-height:1.5; color:#111827;">
                    <h2>Here’s your secure sign-in link for %s</h2>
                    <p>Tap the button below on your iPhone to continue:</p>
                    <p style="margin: 24px 0;">
                      <a href="%s"
                         style="background-color:#10b981;color:#ffffff;padding:12px 20px;border-radius:9999px;
                                text-decoration:none;font-weight:600;display:inline-block;">
                         Continue to %s
                      </a>
                    </p>
                    <p>If the button doesn’t work, copy and paste this link into Safari:</p>
                    <p><a href="%s">%s</a></p>
                    <p style="margin-top:24px;font-size:13px;color:#6b7280;">
                      This link only works once and expires soon. If you didn’t request this, you can safely ignore this email.
                    </p>
                    <p style="font-size:13px;color:#6b7280;">– The %s team</p>
                  </body>
                </html>
                """.formatted(appName, magicLoginUrl, appName, magicLoginUrl, magicLoginUrl, appName);
    }
}
