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
 * Sends simple text payloads to an incoming webhook URL.
 */
@Component
public class SlackNotificationAdapter {

    private static final Logger log = LoggerFactory.getLogger(SlackNotificationAdapter.class);

    private final String webhookUrl;
    private final HttpClient httpClient;

    public SlackNotificationAdapter(
            @Value("${goodbuy.notifications.slack.webhook-url:}") String webhookUrl
    ) {
        this.webhookUrl = (webhookUrl == null ? "" : webhookUrl.trim());
        this.httpClient = HttpClient.newHttpClient();

        if (this.webhookUrl.isBlank()) {
            log.warn("SlackNotificationAdapter initialized WITHOUT webhook URL (notifications disabled)");
        } else {
            log.info("SlackNotificationAdapter initialized WITH webhook URL: ...{}",
                    this.webhookUrl.substring(Math.max(0, this.webhookUrl.length() - 8)));
        }
    }

    public void send(String text) {
        if (webhookUrl == null || webhookUrl.isBlank()) {
            log.info("Slack webhook URL not configured; skipping Slack notification. Message would have been:\n{}",
                    text);
            return;
        }

        try {
            String payload = "{\"text\":" + toJsonString(text) + "}";

            log.info("Sending Slack notification to webhook (len={}):\n{}",
                    webhookUrl.length(), text);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(webhookUrl))
                    .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();

            if (status < 200 || status >= 300) {
                log.warn("Slack webhook returned non-2xx status={} body={}", status, response.body());
            } else {
                log.info("Slack webhook sent successfully (status={})", status);
            }
        } catch (Exception e) {
            log.warn("Error sending Slack notification: {}", e.getMessage(), e);
        }
    }

    private String toJsonString(String s) {
        if (s == null) return "\"\"";
        String escaped = s.replace("\\", "\\\\").replace("\"", "\\\"");
        return "\"" + escaped + "\"";
    }
}
