package app.goodbuy.adapters.core.notifications;

import app.goodbuy.core.notifications.SlackMissingIngredientNotifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Missing-ingredient implementation of SlackMissingIngredientNotifier.
 *
 * - Always logs the missing ingredient.
 * - Delegates actual Slack POST to SlackNotificationAdapter.
 */
@Component
public class LoggingSlackMissingIngredientNotifier implements SlackMissingIngredientNotifier {

    private static final Logger log =
            LoggerFactory.getLogger(LoggingSlackMissingIngredientNotifier.class);

    private final SlackNotificationAdapter slack;

    public LoggingSlackMissingIngredientNotifier(SlackNotificationAdapter slack) {
        this.slack = slack;
        log.info("LoggingSlackMissingIngredientNotifier initialized, SlackNotificationAdapter wired={}",
                slack != null);
    }

    @Override
    public void notifyMissingIngredient(
            String ingredientName,
            String productEan,
            String appVersion,
            String platform,
            String notes
    ) {
        // Always log locally
        log.info(
                "MISSING INGREDIENT reported: name='{}', ean='{}', platform='{}', appVersion='{}', notes='{}'",
                ingredientName, productEan, platform, appVersion, notes
        );

        String text = buildSlackText(ingredientName, productEan, appVersion, platform, notes);

        try {
            log.info("LoggingSlackMissingIngredientNotifier: calling SlackNotificationAdapter.send(...)");
            slack.send(text);
            log.info("LoggingSlackMissingIngredientNotifier: SlackNotificationAdapter.send(...) returned");
        } catch (Exception e) {
            log.warn("LoggingSlackMissingIngredientNotifier: failed to send Slack notification: {}",
                    e.toString(), e);
        }
    }

    // ─────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────

    private String buildSlackText(
            String ingredientName,
            String productEan,
            String appVersion,
            String platform,
            String notes
    ) {
        StringBuilder sb = new StringBuilder();
        sb.append("*Missing Ingredient Reported*").append("\n");
        sb.append("• *Name*: `").append(orDash(ingredientName)).append("`\n");
        sb.append("• *EAN*: `").append(orDash(productEan)).append("`\n");
        sb.append("• *Platform*: ").append(orDash(platform)).append("\n");
        sb.append("• *App Version*: ").append(orDash(appVersion)).append("\n");
        if (notes != null && !notes.isBlank()) {
            sb.append("• *Notes*: ").append(notes);
        }
        return sb.toString();
    }

    private static String orDash(String v) {
        return (v == null || v.isBlank()) ? "-" : v;
    }
}
