package app.goodbuy.core.notifications;

/**
 * Core-side port for notifying about missing ingredients.
 *
 * Implementations (e.g. Slack, email, etc.) should live in adapter modules.
 */
public interface SlackMissingIngredientNotifier {

    /**
     * Notify that an ingredient is missing from the GoodBuy DB.
     *
     * @param ingredientName exact ingredient name as seen by the user
     * @param productEan     product EAN/GTIN if available (may be null)
     * @param appVersion     client app version (may be null)
     * @param platform       platform (e.g. "iOS", "Android") (may be null)
     * @param notes          any free-form notes or context (may be null)
     */
    void notifyMissingIngredient(
            String ingredientName,
            String productEan,
            String appVersion,
            String platform,
            String notes
    );
}
