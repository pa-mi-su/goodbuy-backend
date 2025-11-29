package app.goodbuy.adapters.core.products.service;

import app.goodbuy.adapters.core.notifications.SlackNotificationAdapter;
import app.goodbuy.adapters.core.products.model.MissingProductReportEntity;
import app.goodbuy.adapters.core.products.repo.MissingProductReportRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

@Service
@Transactional
public class MissingProductReportService {

    private static final Logger log =
            LoggerFactory.getLogger(MissingProductReportService.class);

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
     * Small result type so callers (like the API controller) can know whether
     * this was the first time this EAN was reported (isNew == true) or if it
     * was already in the DB (isNew == false).
     */
    public record MissingProductReportResult(
            MissingProductReportEntity entity,
            boolean isNew
    ) {}

    /**
     * New API: does the full insert/update + Slack-once logic, and returns
     * both the saved entity and a flag telling you if this report was new.
     *
     * Dedup rule:
     *  - ONE ROW PER EAN in product_missing_report.
     *  - If an entry for that EAN already exists, we update it in-place.
     *  - Otherwise, we insert a new row.
     *
     * Slack rule:
     *  - For a given EAN, send Slack **only on the very first insert**.
     *  - Subsequent reports update the row but do NOT send Slack again.
     */
    public MissingProductReportResult reportWithStatus(
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

        // Defensive guard
        if (productEan == null || productEan.isBlank()) {
            log.warn("MissingProductReportService.reportWithStatus called with null/blank EAN; rejecting request");
            throw new IllegalArgumentException("productEan must not be null or blank");
        }

        // 1) Insert-or-update by EAN
        MissingProductReportEntity e = repo.findByEan(productEan)
                .orElseGet(MissingProductReportEntity::new);

        boolean isNew = (e.getId() == null);

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

        // Persist S3 image URLs
        e.setFrontImageS3Url(frontImageUrl);
        e.setBackImageS3Url(backImageUrl);

        // "occurredAt" = last time we saw this product missing
        e.setOccurredAt(now);

        MissingProductReportEntity saved = repo.save(e);

        // 2) Slack notification ONLY for brand new rows
        if (isNew) {
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

                log.info("MissingProductReportService: first time for ean='{}' → sending Slack", productEan);
                slack.send(text);
            } catch (Exception ex) {
                log.warn("MissingProductReportService: failed to send Slack notification: {}", ex.toString());
            }
        } else {
            log.info("MissingProductReportService: ean='{}' already reported → skipping Slack", productEan);
        }

        return new MissingProductReportResult(saved, isNew);
    }

    /**
     * Old API kept for backwards compatibility.
     *
     * Existing callers still compile and behave the same, just ignoring
     * the "isNew / alreadyReported" status.
     *
     * The controller will be moved to use reportWithStatus(...) in the
     * next step so it can tell the app if this was already reported.
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
        MissingProductReportResult result = reportWithStatus(
                productEan,
                productName,
                brandName,
                frontImageUrl,
                backImageUrl,
                appVersion,
                platform,
                notes
        );
        return result.entity();
    }

    // ─────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────

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
