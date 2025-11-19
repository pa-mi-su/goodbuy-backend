package app.goodbuy.adapters.core.ingredients.service;

import app.goodbuy.adapters.core.ingredients.model.MissingIngredientReportEntity;
import app.goodbuy.adapters.core.ingredients.repo.MissingIngredientReportRepository;
import app.goodbuy.adapters.core.notifications.SlackNotificationAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;

@Service
@Transactional
public class MissingIngredientReportService {

    private static final Logger log =
            LoggerFactory.getLogger(MissingIngredientReportService.class);

    /**
     * How often we’re willing to send a Slack notification for the same
     * (ingredientName, productEan) pair. Everything faster than this will
     * still update the DB row but will NOT send another Slack.
     */
    private static final Duration SLACK_THROTTLE_WINDOW = Duration.ofMinutes(10);

    private final MissingIngredientReportRepository repo;
    private final SlackNotificationAdapter slack;

    public MissingIngredientReportService(
            MissingIngredientReportRepository repo,
            SlackNotificationAdapter slack
    ) {
        this.repo = repo;
        this.slack = slack;
        log.info("MissingIngredientReportService initialized, SlackNotificationAdapter wired={}",
                slack != null);
    }

    /**
     * Persist a missing-ingredient report and (throttled) Slack notification.
     *
     * Dedup rule:
     *  - ONE ROW per (ingredientName, productEan) in ingredient_missing_report.
     *  - If an entry for that combination already exists, we update it.
     *  - Otherwise, we insert a new row.
     *
     * Slack rule:
     *  - For a given (ingredientName, productEan), send at most one Slack
     *    notification per SLACK_THROTTLE_WINDOW.
     */
    public MissingIngredientReportEntity report(
            String ingredientName,
            String productEan,
            String appVersion,
            String platform,
            String notes
    ) {
        OffsetDateTime now = OffsetDateTime.now();

        // Defensive guards – these two are our natural key
        if (ingredientName == null || ingredientName.isBlank()) {
            log.warn("MissingIngredientReportService.report called with null/blank ingredientName; rejecting request");
            throw new IllegalArgumentException("ingredientName must not be null or blank");
        }
        if (productEan == null || productEan.isBlank()) {
            log.warn("MissingIngredientReportService.report called with null/blank productEan; rejecting request");
            throw new IllegalArgumentException("productEan must not be null or blank");
        }

        // 1) Insert-or-update by (ingredientName, productEan)
        MissingIngredientReportEntity e = repo
                .findByIngredientNameAndProductEan(ingredientName, productEan)
                .orElseGet(MissingIngredientReportEntity::new);

        boolean isNew = (e.getId() == null);
        OffsetDateTime previousOccurredAt = e.getOccurredAt(); // may be null

        if (isNew) {
            log.info("MissingIngredientReportService: creating new missing-ingredient row for ingredient='{}' ean={}",
                    ingredientName, productEan);
            e.setIngredientName(ingredientName);
            e.setProductEan(productEan);
            // If your entity has createdAt, you can set it here:
            // e.setCreatedAt(now);
        } else {
            log.info("MissingIngredientReportService: updating existing missing-ingredient row for ingredient='{}' ean={}",
                    ingredientName, productEan);
        }

        // Always refresh “last seen” details
        e.setAppVersion(appVersion);
        e.setPlatform(platform);
        e.setNotes(notes);
        e.setOccurredAt(now);  // last time we saw this missing ingredient

        MissingIngredientReportEntity saved = repo.save(e);

        // 2) Throttled Slack notification (best-effort)
        if (shouldSendSlack(previousOccurredAt, now)) {
            try {
                String text = buildSlackText(
                        ingredientName,
                        productEan,
                        appVersion,
                        platform,
                        notes
                );

                log.info("MissingIngredientReportService: calling SlackNotificationAdapter.send(...)");
                slack.send(text);
                log.info("MissingIngredientReportService: SlackNotificationAdapter.send(...) returned");
            } catch (Exception ex) {
                log.warn("MissingIngredientReportService: failed to send Slack notification: {}", ex.toString());
            }
        } else {
            log.info(
                    "MissingIngredientReportService: throttling Slack for ingredient='{}' ean='{}' (within {})",
                    ingredientName,
                    productEan,
                    SLACK_THROTTLE_WINDOW
            );
        }

        return saved;
    }

    // ─────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────

    private boolean shouldSendSlack(OffsetDateTime previousOccurredAt, OffsetDateTime now) {
        if (previousOccurredAt == null) {
            // New row: always send Slack
            return true;
        }
        OffsetDateTime nextAllowed = previousOccurredAt.plus(SLACK_THROTTLE_WINDOW);
        return now.isAfter(nextAllowed);
    }

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
