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
import java.util.Set;
import java.util.UUID;

@Service
@Transactional
public class ProductEvidenceReportService {

    private static final Logger log =
            LoggerFactory.getLogger(ProductEvidenceReportService.class);

    // ✅ Canonical reasons (3 scenarios)
    public static final String REASON_MISSING_PRODUCT = "missing_product";
    public static final String REASON_UNCLEAR_INGREDIENTS = "unclear_ingredients";
    public static final String REASON_OUT_OF_DOMAIN = "out_of_domain";

    private static final Set<String> ALLOWED_REASONS = Set.of(
            REASON_MISSING_PRODUCT,
            REASON_UNCLEAR_INGREDIENTS,
            REASON_OUT_OF_DOMAIN
    );

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
        if (!ALLOWED_REASONS.contains(reasonNorm)) {
            throw new IllegalArgumentException("unsupported reason: " + reasonNorm);
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

        // Always update “latest report” fields (safe + useful)
        entity.setProductName(trimOrNull(productName));
        entity.setBrand(trimOrNull(brand));
        entity.setAppVersion(trimOrNull(appVersion));
        entity.setPlatform(trimOrNull(platform));
        entity.setNotes(trimOrNull(notes));
        entity.setOccurredAt(now);

        boolean frontProvided = (frontImageBytes != null && frontImageBytes.length > 0);
        boolean backProvided  = (backImageBytes  != null && backImageBytes.length  > 0);

        if (frontProvided) {
            String url = uploadToS3(eanNorm, reasonNorm, "front", frontImageBytes, frontContentType);
            entity.setFrontImageS3Url(url);
        }

        if (backProvided) {
            String url = uploadToS3(eanNorm, reasonNorm, "back", backImageBytes, backContentType);
            entity.setBackImageS3Url(url);
        }

        ProductEvidenceReportEntity saved = repo.save(entity);

        // Slack:
        // - always notify on first report
        // - notify again if user provided new images (so Slack has URLs)
        if (isNew || frontProvided || backProvided) {
            slack.send(buildSlackText(saved));
        }

        return new ProductEvidenceReportResult(saved, isNew);
    }

    // ───────────────── helpers ─────────────────

    private String uploadToS3(String ean, String reason, String kind, byte[] bytes, String contentType) {
        String ct = (contentType == null || contentType.isBlank())
                ? "image/jpeg"
                : contentType;

        // Include reason in key so images don’t collide between scenarios.
        String key = "product-evidence/"
                + reason + "/"
                + ean + "/"
                + kind + "/"
                + DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(OffsetDateTime.now()).replace(":", "")
                + "_"
                + UUID.randomUUID().toString().replace("-", "")
                + ".jpg";

        String url = imageStorage.uploadImage(key, bytes, ct);
        log.info("ProductEvidenceReportService: uploaded {} image to S3 ean={} reason={} url={}", kind, ean, reason, url);
        return url;
    }

    private String buildSlackText(ProductEvidenceReportEntity e) {
        return "*Product Evidence Reported*\n"
                + "• EAN: `" + e.getEan() + "`\n"
                + "• Reason: `" + e.getReason() + "`\n"
                + "• Name: " + orDash(e.getProductName()) + "\n"
                + "• Brand: " + orDash(e.getBrand()) + "\n"
                + "• Notes: " + orDash(e.getNotes()) + "\n"
                + "• Front Image: " + orDash(e.getFrontImageS3Url()) + "\n"
                + "• Back Image: " + orDash(e.getBackImageS3Url());
    }

    private static String orDash(String v) {
        return (v == null || v.isBlank()) ? "-" : v;
    }

    private static String trimOrNull(String v) {
        if (v == null) return null;
        String t = v.trim();
        return t.isEmpty() ? null : t;
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
