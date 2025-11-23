package app.goodbuy.core.notifications;

/**
 * Generic notification port.
 * Implementations may deliver messages to:
 *  - Slack
 *  - Email
 *  - SMS
 *  - Webhook
 *
 * This is intentionally minimal and extendable.
 */
public interface NotificationPort {

    /**
     * Send a free-form notification message.
     * Implementations should NEVER throw exceptions outward.
     */
    void send(String message);
}
