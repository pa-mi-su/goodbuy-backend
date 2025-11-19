package app.goodbuy.adapters.core.products.service;

import app.goodbuy.adapters.core.notifications.SlackNotificationAdapter;
import app.goodbuy.adapters.core.products.model.MissingProductReportEntity;
import app.goodbuy.adapters.core.products.repo.MissingProductReportRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;

@Service
@Transactional
public class MissingProductReportService {

    private static final Logger log =
            LoggerFactory.getLogger(MissingProductReportService.class);

    /**
     * How often we’re willing to send a Slack notification for the same EAN.
     * Requests inside this window still update the DB row but skip Slack.
     */
    private static final Duration SLACK_THROTTLE_WINDOW = Duration.ofMinutes(10);

    private final MissingProductReportRepository repo;
    private final SlackNotificationAdapter slack;

    public MissingProductReportService(
            MissingProductReportRepository repo,
            SlackNotificationAdapter slack
    ) {
        this.repo = repo;
        this.slack = slack;
        log.info("MissingProductReportService initialized, SlackNotificationAdapter wired={}",
                slack != null);
    }

    /**
     * Persist a missing-product report and (throttled) Slack notification.
     *
     * Dedup rule:
     *  - ONE ROW PER EAN in product_missing_report.
     *  - If an entry for that EAN already exists, we update it in-place.
     *  - Otherwise, we insert a new row.
     *
     * Slack rule:
     *  - For a given EAN, send at most one Slack notification per
     *    SLACK_THROTTLE_WINDOW.
     */
    public MissingProductReportEntity report(
            String productEan,
            String productName,
            String brandName,
            String frontImageUrl,
            String backImageUrl,
            String appVersion,
            String platform,
            String notes
    ) {
        OffsetDateTime now = OffsetDateTime.now();

        // Defensive guard: EAN is NOT NULL in the DB, so if we ever get here
        // with a blank EAN, better to log loud and fail fast.
        if (productEan == null || productEan.isBlank()) {
            log.warn("MissingProductReportService.report called with null/blank EAN; rejecting request");
            throw new IllegalArgumentException("productEan must not be null or blank");
        }

        // 1) Insert-or-update by EAN
        MissingProductReportEntity e = repo.findByEan(productEan)
                .orElseGet(MissingProductReportEntity::new);

        boolean isNew = (e.getId() == null);
        OffsetDateTime previousOccurredAt = e.getOccurredAt(); // may be null

        if (isNew) {
            // First time we see this EAN → create row
            e.setEan(productEan);   // NOT NULL column
            e.setCreatedAt(now);    // first time we saw this product missing
            log.info("MissingProductReportService: creating new missing-product row for ean={}", productEan);
        } else {
            log.info("MissingProductReportService: updating existing missing-product row for ean={}", productEan);
        }

        // Always refresh these fields with the latest info we have
        e.setProductName(productName);
        e.setBrand(brandName);
        e.setAppVersion(appVersion);
        e.setPlatform(platform);
        e.setNotes(notes);
        // "occurredAt" = last time we saw this product missing
        e.setOccurredAt(now);

        MissingProductReportEntity saved = repo.save(e);

        // 2) Throttled Slack notification (best-effort)
        if (shouldSendSlack(previousOccurredAt, now)) {
            try {
                String text = buildSlackText(
                        productEan,
                        productName,
                        brandName,
                        frontImageUrl,
                        backImageUrl,
                        appVersion,
                        platform,
                        notes
                );

                log.info("MissingProductReportService: calling SlackNotificationAdapter.send(...)");
                slack.send(text);
                log.info("MissingProductReportService: SlackNotificationAdapter.send(...) returned");
            } catch (Exception ex) {
                log.warn("MissingProductReportService: failed to send Slack notification: {}", ex.toString());
            }
        } else {
            log.info(
                    "MissingProductReportService: throttling Slack for ean='{}' (within {})",
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
            String productEan,
            String productName,
            String brandName,
            String frontImageUrl,
            String backImageUrl,
            String appVersion,
            String platform,
            String notes
    ) {
        StringBuilder sb = new StringBuilder();
        sb.append("*Missing Product Reported*").append("\n");
        sb.append("• *EAN*: `").append(orDash(productEan)).append("`\n");
        sb.append("• *Name*: ").append(orDash(productName)).append("\n");
        sb.append("• *Brand*: ").append(orDash(brandName)).append("\n");
        sb.append("• *Platform*: ").append(orDash(platform)).append("\n");
        sb.append("• *App Version*: ").append(orDash(appVersion)).append("\n");
        if (frontImageUrl != null && !frontImageUrl.isBlank()) {
            sb.append("• *Front image URL*: ").append(frontImageUrl).append("\n");
        }
        if (backImageUrl != null && !backImageUrl.isBlank()) {
            sb.append("• *Back image URL*: ").append(backImageUrl).append("\n");
        }
        if (notes != null && !notes.isBlank()) {
            sb.append("• *Notes*: ").append(notes);
        }
        return sb.toString();
    }

    private static String orDash(String v) {
        return (v == null || v.isBlank()) ? "-" : v;
    }
}
