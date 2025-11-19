package app.goodbuy.adapters.core.notifications;

import app.goodbuy.core.notifications.SlackMissingIngredientNotifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Adapter-side implementation of SlackMissingIngredientNotifier.
 *
 * For now it just logs the event – later we can wire this into a real
 * SlackNotificationAdapter or email sender without touching core.
 */
@Service
public class LoggingSlackMissingIngredientNotifier implements SlackMissingIngredientNotifier {

    private static final Logger log =
            LoggerFactory.getLogger(LoggingSlackMissingIngredientNotifier.class);

    @Override
    public void notifyMissingIngredient(
            String ingredientName,
            String productEan,
            String appVersion,
            String platform,
            String notes
    ) {
        log.info(
                "MISSING INGREDIENT reported: name='{}', ean='{}', platform='{}', appVersion='{}', notes='{}'",
                ingredientName,
                productEan,
                platform,
                appVersion,
                notes
        );
    }
}
