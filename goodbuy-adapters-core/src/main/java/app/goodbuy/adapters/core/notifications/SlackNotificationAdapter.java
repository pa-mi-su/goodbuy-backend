package app.goodbuy.adapters.core.notifications;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/**
 * Low-level Slack webhook adapter.
 * Supports a default webhook + an ingredients-specific webhook.
 */
@Component
public class SlackNotificationAdapter {

    private static final Logger log = LoggerFactory.getLogger(SlackNotificationAdapter.class);

    private final String defaultWebhookUrl;
    private final String ingredientsWebhookUrl;
    private final HttpClient httpClient;

    public SlackNotificationAdapter(
            @Value("${goodbuy.notifications.slack.webhook-url:}") String defaultWebhookUrl,
            @Value("${goodbuy.notifications.slack.ingredients-webhook-url:}") String ingredientsWebhookUrl
    ) {
        this.defaultWebhookUrl = normalize(defaultWebhookUrl);
        this.ingredientsWebhookUrl = normalize(ingredientsWebhookUrl);
        this.httpClient = HttpClient.newHttpClient();

        if (this.defaultWebhookUrl.isBlank()) {
            log.warn("SlackNotificationAdapter: DEFAULT webhook NOT configured (generic notifications disabled)");
        } else {
            log.info("SlackNotificationAdapter: DEFAULT webhook configured ...{}",
                    last8(this.defaultWebhookUrl));
        }

        if (this.ingredientsWebhookUrl.isBlank()) {
            log.warn("SlackNotificationAdapter: INGREDIENTS webhook NOT configured (ingredient alerts disabled)");
        } else {
            log.info("SlackNotificationAdapter: INGREDIENTS webhook configured ...{}",
                    last8(this.ingredientsWebhookUrl));
        }
    }

    // ─────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────

    /** Generic notifications to the default channel. */
    public void send(String text) {
        sendToWebhook(defaultWebhookUrl, "default", text);
    }

    /** Ingredient-missing notifications to the ingredients channel. */
    public void sendIngredientMissing(String text) {
        sendToWebhook(ingredientsWebhookUrl, "ingredients", text);
    }

    // ─────────────────────────────────────────────
    // Internal helpers
    // ─────────────────────────────────────────────

    private void sendToWebhook(String webhookUrl, String channelKey, String text) {
        if (webhookUrl == null || webhookUrl.isBlank()) {
            log.info(
                    "SlackNotificationAdapter[{}]: webhook URL not configured; " +
                            "skipping Slack notification. Message would have been:\n{}",
                    channelKey, text
            );
            return;
        }

        try {
            String payload = "{\"text\":" + toJsonString(text) + "}";

            log.info("SlackNotificationAdapter[{}]: sending notification (len={}):\n{}",
                    channelKey, webhookUrl.length(), text);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(webhookUrl))
                    .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();

            if (status < 200 || status >= 300) {
                log.warn("SlackNotificationAdapter[{}]: non-2xx status={} body={}",
                        channelKey, status, response.body());
            } else {
                log.info("SlackNotificationAdapter[{}]: sent successfully (status={})",
                        channelKey, status);
            }
        } catch (Exception e) {
            log.warn("SlackNotificationAdapter[{}]: error sending Slack notification: {}",
                    channelKey, e.getMessage(), e);
        }
    }

    private static String normalize(String s) {
        return (s == null) ? "" : s.trim();
    }

    private static String last8(String s) {
        return s.substring(Math.max(0, s.length() - 8));
    }

    private String toJsonString(String s) {
        if (s == null) return "\"\"";
        String escaped = s.replace("\\", "\\\\").replace("\"", "\\\"");
        return "\"" + escaped + "\"";
    }
}
