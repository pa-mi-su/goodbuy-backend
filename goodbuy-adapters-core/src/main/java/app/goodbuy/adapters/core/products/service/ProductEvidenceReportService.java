package app.goodbuy.adapters.core.products.service;

import app.goodbuy.adapters.core.notifications.SlackNotificationAdapter;
import app.goodbuy.adapters.core.products.model.ProductEvidenceReportEntity;
import app.goodbuy.adapters.core.products.repo.ProductEvidenceReportRepository;
import app.goodbuy.core.storage.ProductImageStoragePort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;

@Service
@Transactional
public class ProductEvidenceReportService {

    private static final Logger log =
            LoggerFactory.getLogger(ProductEvidenceReportService.class);

    private final ProductEvidenceReportRepository repo;
    private final SlackNotificationAdapter slack;
    private final ProductImageStoragePort imageStorage;

    public ProductEvidenceReportService(
            ProductEvidenceReportRepository repo,
            SlackNotificationAdapter slack,
            ProductImageStoragePort imageStorage
    ) {
        this.repo = repo;
        this.slack = slack;
        this.imageStorage = imageStorage;
    }

    public record ProductEvidenceReportResult(
            ProductEvidenceReportEntity entity,
            boolean isNew
    ) {}

    public ProductEvidenceReportResult reportWithStatus(
            String ean,
            String reason,
            String productName,
            String brand,
            String appVersion,
            String platform,
            String notes,
            byte[] frontImageBytes,
            String frontContentType,
            byte[] backImageBytes,
            String backContentType
    ) {
        OffsetDateTime now = OffsetDateTime.now();

        String eanNorm = normalizeToGtin14DigitsOnly(ean);
        if (eanNorm == null) {
            throw new IllegalArgumentException("ean must contain 12, 13, or 14 digits");
        }

        String reasonNorm = (reason == null ? "" : reason).trim().toLowerCase(Locale.ROOT);
        if (reasonNorm.isBlank()) {
            throw new IllegalArgumentException("reason is required");
        }

        ProductEvidenceReportEntity entity = repo
                .findByEanAndReason(eanNorm, reasonNorm)
                .orElseGet(ProductEvidenceReportEntity::new);

        boolean isNew = (entity.getId() == null);

        if (isNew) {
            entity.setEan(eanNorm);
            entity.setReason(reasonNorm);
            entity.setCreatedAt(now);
        }

        // Always update “latest report” fields
        entity.setProductName(productName);
        entity.setBrand(brand);
        entity.setAppVersion(appVersion);
        entity.setPlatform(platform);
        entity.setNotes(notes);
        entity.setOccurredAt(now);

        // ✅ FIX: store images for BOTH reasons (missing_product AND unclear_ingredients).
        // (If you later add more reasons, this is still safe.)
        boolean frontProvided = (frontImageBytes != null && frontImageBytes.length > 0);
        boolean backProvided  = (backImageBytes  != null && backImageBytes.length  > 0);

        if (frontProvided) {
            String url = uploadToS3(eanNorm, "front", frontImageBytes, frontContentType);
            entity.setFrontImageS3Url(url);
        }

        if (backProvided) {
            String url = uploadToS3(eanNorm, "back", backImageBytes, backContentType);
            entity.setBackImageS3Url(url);
        }

        ProductEvidenceReportEntity saved = repo.save(entity);

        // ✅ Improve Slack behavior:
        // - Always notify on first report
        // - Also notify if new images were provided (so Slack shows URLs)
        if (isNew || frontProvided || backProvided) {
            slack.send(buildSlackText(saved));
        }

        return new ProductEvidenceReportResult(saved, isNew);
    }

    // ───────────────── helpers ─────────────────

    private String uploadToS3(String ean, String kind, byte[] bytes, String contentType) {
        String ct = (contentType == null || contentType.isBlank())
                ? "image/jpeg"
                : contentType;

        String key = "product-evidence/"
                + ean + "/"
                + kind + "/"
                + DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(OffsetDateTime.now()).replace(":", "")
                + "_"
                + UUID.randomUUID().toString().replace("-", "")
                + ".jpg";

        String url = imageStorage.uploadImage(key, bytes, ct);
        log.info("ProductEvidenceReportService: uploaded {} image to S3 ean={} url={}", kind, ean, url);
        return url;
    }

    private String buildSlackText(ProductEvidenceReportEntity e) {
        return "*Product Evidence Reported*\n"
                + "• EAN: `" + e.getEan() + "`\n"
                + "• Reason: `" + e.getReason() + "`\n"
                + "• Name: " + orDash(e.getProductName()) + "\n"
                + "• Brand: " + orDash(e.getBrand()) + "\n"
                + "• Front Image: " + orDash(e.getFrontImageS3Url()) + "\n"
                + "• Back Image: " + orDash(e.getBackImageS3Url());
    }

    private static String orDash(String v) {
        return (v == null || v.isBlank()) ? "-" : v;
    }

    private static String normalizeToGtin14DigitsOnly(String raw) {
        String digits = raw == null ? "" : raw.replaceAll("\\D+", "");
        return switch (digits.length()) {
            case 14 -> digits;
            case 13 -> "0" + digits;
            case 12 -> "00" + digits;
            default -> null;
        };
    }
}
