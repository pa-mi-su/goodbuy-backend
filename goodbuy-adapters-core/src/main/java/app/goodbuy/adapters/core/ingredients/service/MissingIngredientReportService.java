package app.goodbuy.adapters.core.ingredients.service;

import app.goodbuy.adapters.core.ingredients.model.MissingIngredientReportEntity;
import app.goodbuy.adapters.core.ingredients.repo.MissingIngredientReportRepository;
import app.goodbuy.adapters.core.notifications.SlackNotificationAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

@Service
@Transactional
public class MissingIngredientReportService {

    private static final Logger log =
            LoggerFactory.getLogger(MissingIngredientReportService.class);

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
     * Lightweight result wrapper so callers (API layer) can know
     * whether this was the *first* time we saw this ingredientName
     * globally (any productEan) or a repeat of an already-reported one.
     *
     *  - isNew = true  → first time this ingredientName was reported (global)
     *  - isNew = false → ingredientName already existed (global)
     */
    public record MissingIngredientReportResult(
            MissingIngredientReportEntity entity,
            boolean isNew
    ) {}

    /**
     * New API: persist a missing-ingredient report and (first-only) Slack notification.
     *
     * Dedup rule (GLOBAL):
     *  - ONE ROW per *normalized ingredientName* in ingredient_missing_report.
     *  - If an entry for that ingredientName already exists (any productEan),
     *    we update it in-place.
     *  - Otherwise, we insert a new row.
     *
     * Slack rule:
     *  - For a given ingredientName, send Slack **only once**,
     *    i.e. when the row is first created.
     *
     * productEan is optional – context only; it no longer participates in the dedup key.
     */
    public MissingIngredientReportResult reportWithStatus(
            String ingredientName,
            String productEan,
            String appVersion,
            String platform,
            String notes
    ) {
        OffsetDateTime now = OffsetDateTime.now();

        // ingredientName is the global key
        if (ingredientName == null || ingredientName.isBlank()) {
            log.warn("MissingIngredientReportService.reportWithStatus called with null/blank ingredientName; rejecting request");
            throw new IllegalArgumentException("ingredientName must not be null or blank");
        }

        // productEan is OPTIONAL now; still useful for context / Slack.
        String normalizedIngredientName = normalizeIngredientName(ingredientName);
        String productGtin14 = normalizeToGtin14(productEan); // may be null

        log.debug("MissingIngredientReportService.reportWithStatus: ingredient(raw)='{}' normalized='{}' ean(raw)={} gtin14={}",
                ingredientName, normalizedIngredientName, productEan, productGtin14);

        // 1) Insert-or-update by ingredientName ONLY (GLOBAL by ingredient)
        //
        // NOTE: repo has both:
        //   - findByIngredientNameIgnoreCase(...)
        //   - findByIngredientNameAndProductEan(...)  (kept for any legacy use)
        MissingIngredientReportEntity e = repo
                .findByIngredientNameIgnoreCase(normalizedIngredientName)
                .orElseGet(MissingIngredientReportEntity::new);

        boolean isNew = (e.getId() == null);

        if (isNew) {
            log.info(
                    "MissingIngredientReportService: creating new missing-ingredient row for ingredient='{}' (global key)",
                    normalizedIngredientName
            );
            // We store the normalized name as the canonical key
            e.setIngredientName(normalizedIngredientName);
            // createdAt / occurredAt via @PrePersist; we still set occurredAt below
        } else {
            log.info(
                    "MissingIngredientReportService: updating existing missing-ingredient row for ingredient='{}' (global key)",
                    normalizedIngredientName
            );
        }

        // Always refresh latest context.
        // We keep the latest productEan + metadata, but they do NOT affect dedup.
        e.setProductEan(productEan);
        e.setAppVersion(appVersion);
        e.setPlatform(platform);
        e.setNotes(notes);
        e.setOccurredAt(now);  // last time we saw this missing ingredient

        MissingIngredientReportEntity saved = repo.save(e);

        // 2) Slack notification: ONLY for brand-new rows
        if (isNew) {
            try {
                String text = buildSlackText(
                        normalizedIngredientName,
                        productEan,
                        productGtin14,
                        appVersion,
                        platform,
                        notes
                );

                log.info("MissingIngredientReportService: first time for ingredient='{}' → sending Slack", normalizedIngredientName);
                slack.send(text);
                log.info("MissingIngredientReportService: SlackNotificationAdapter.send(...) returned");
            } catch (Exception ex) {
                log.warn("MissingIngredientReportService: failed to send Slack notification: {}", ex.toString());
            }
        } else {
            log.info(
                    "MissingIngredientReportService: ingredient='{}' already reported (global) → skipping Slack",
                    normalizedIngredientName
            );
        }

        return new MissingIngredientReportResult(saved, isNew);
    }

    /**
     * Backwards-compatible API for any older callers.
     * Always returns the entity; Slack still only fires on first report.
     */
    public MissingIngredientReportEntity report(
            String ingredientName,
            String productEan,
            String appVersion,
            String platform,
            String notes
    ) {
        return reportWithStatus(
                ingredientName,
                productEan,
                appVersion,
                platform,
                notes
        ).entity();
    }

    // ─────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────

    /**
     * Normalize ingredient name for use as a GLOBAL key:
     *  - trim
     *  - lower-case
     */
    private String normalizeIngredientName(String raw) {
        if (raw == null) return null;
        return raw.trim().toLowerCase();
    }

    /**
     * Normalize a raw EAN/UPC to a 14-digit GTIN where possible.
     *  - Strips non-digits
     *  - If >= 14 digits, uses the last 14
     *  - If 13 digits, pads with one leading '0'
     *  - If 12 digits, pads with two leading '0'
     *  - Otherwise, returns whatever digits we have (best effort)
     */
    private String normalizeToGtin14(String rawEan) {
        if (rawEan == null) {
            return null;
        }
        String digits = rawEan.replaceAll("\\D", "");
        if (digits.length() >= 14) {
            // take the right-most 14 digits
            return digits.substring(digits.length() - 14);
        } else if (digits.length() == 13) {
            return "0" + digits;
        } else if (digits.length() == 12) {
            return "00" + digits;
        }
        // best effort fallback
        return digits;
    }

    private String buildSlackText(
            String ingredientNameNormalized,
            String productEanRaw,
            String productGtin14,
            String appVersion,
            String platform,
            String notes
    ) {
        StringBuilder sb = new StringBuilder();
        sb.append("*Missing Ingredient Reported*").append("\n");
        sb.append("• *Ingredient (normalized)*: `").append(orDash(ingredientNameNormalized)).append("`\n");
        sb.append("• *EAN (raw)*: `").append(orDash(productEanRaw)).append("`\n");
        sb.append("• *EAN (GTIN-14)*: `").append(orDash(productGtin14)).append("`\n");
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
