package app.goodbuy.adapters.core.products.service;

import app.goodbuy.adapters.core.notifications.SlackNotificationAdapter;
import app.goodbuy.adapters.core.products.model.ProductEvidenceReportEntity;
import app.goodbuy.adapters.core.products.repo.ProductEvidenceReportRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.Set;

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

    public static final String STATUS_REPORTED = "REPORTED";
    public static final String STATUS_RESOLVED = "RESOLVED";

    private final ProductEvidenceReportRepository repo;
    private final SlackNotificationAdapter slack;

    public ProductEvidenceReportService(
            ProductEvidenceReportRepository repo,
            SlackNotificationAdapter slack
    ) {
        this.repo = repo;
        this.slack = slack;
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
            String notes
    ) {
        OffsetDateTime now = OffsetDateTime.now();

        String eanNorm = normalizeToGtin14DigitsOnly(ean);
        if (eanNorm == null) {
            throw new IllegalArgumentException("ean must contain 12, 13, or 14 digits");
        }

        String reasonNorm = normalizeReason(reason);
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
            entity.setStatus(STATUS_REPORTED); // explicit, matches DB default
        }

        // Defensive: never allow blank status to hit DB
        if (entity.getStatus() == null || entity.getStatus().isBlank()) {
            entity.setStatus(STATUS_REPORTED);
        }

        // Always update “latest report” fields
        entity.setProductName(trimOrNull(productName));
        entity.setBrand(trimOrNull(brand));
        entity.setAppVersion(trimOrNull(appVersion));
        entity.setPlatform(trimOrNull(platform));
        entity.setNotes(trimOrNull(notes));
        entity.setOccurredAt(now);

        ProductEvidenceReportEntity saved = repo.save(entity);

        if (isNew) {
            slack.send(buildSlackText(saved));
        }

        return new ProductEvidenceReportResult(saved, isNew);
    }

    // ───────────────── helpers ─────────────────

    private String buildSlackText(ProductEvidenceReportEntity e) {
        return "*Product Evidence Reported*\n"
                + "• EAN: `" + e.getEan() + "`\n"
                + "• Reason: `" + e.getReason() + "`\n"
                + "• Status: `" + orDash(e.getStatus()) + "`\n"
                + "• Name: " + orDash(e.getProductName()) + "\n"
                + "• Brand: " + orDash(e.getBrand()) + "\n"
                + "• Notes: " + orDash(e.getNotes());
    }

    private static String orDash(String v) {
        return (v == null || v.isBlank()) ? "-" : v;
    }

    private static String trimOrNull(String v) {
        if (v == null) return null;
        String t = v.trim();
        return t.isEmpty() ? null : t;
    }

    private static String normalizeReason(String reason) {
        return (reason == null ? "" : reason).trim().toLowerCase(Locale.ROOT);
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
