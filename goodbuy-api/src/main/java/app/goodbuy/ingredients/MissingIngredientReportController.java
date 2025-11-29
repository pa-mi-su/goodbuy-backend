package app.goodbuy.api.ingredients;

import app.goodbuy.adapters.core.ingredients.model.MissingIngredientReportEntity;
import app.goodbuy.adapters.core.ingredients.service.MissingIngredientReportService;
import jakarta.validation.constraints.NotBlank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/**
 * API endpoint for reporting missing ingredients from the mobile apps.
 *
 * iOS flow:
 *  - User taps "We don't have this ingredient, notify us".
 *  - App POSTs here with ingredient name + optional product EAN + context.
 *
 * Backend behavior (GLOBAL by ingredient name):
 *  - We persist the report and, on the *first* ever report for that
 *    ingredientName (any productEan), we send a Slack notification.
 *  - Later calls for the same ingredientName only update the row; no extra Slack.
 *  - `alreadyReported` in the response is:
 *      - false → first ever time this ingredientName was seen
 *      - true  → ingredientName has been reported before (any product)
 */
@RestController
@RequestMapping("/api/v1/ingredients/missing")
public class MissingIngredientReportController {

    private static final Logger log = LoggerFactory.getLogger(MissingIngredientReportController.class);

    private final MissingIngredientReportService service;

    public MissingIngredientReportController(MissingIngredientReportService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public MissingIngredientReportResponse reportMissing(
            @RequestBody MissingIngredientReportRequest req
    ) {
        MissingIngredientReportService.MissingIngredientReportResult result =
                service.reportWithStatus(
                        req.ingredientName(),
                        req.productEan(),
                        req.appVersion(),
                        req.platform(),
                        req.notes()
                );

        MissingIngredientReportEntity saved = result.entity();
        boolean alreadyReported = !result.isNew();

        log.info(
                "Missing ingredient reported ingredient='{}' ean={} platform={} version={} alreadyReported={}",
                req.ingredientName(),
                req.productEan(),
                req.platform(),
                req.appVersion(),
                alreadyReported
        );

        return new MissingIngredientReportResponse(saved.getId(), alreadyReported);
    }

    // ─────────────────────────────────────────────────────────────
    // Request + Response DTOs
    // ─────────────────────────────────────────────────────────────

    public record MissingIngredientReportRequest(
            @NotBlank String ingredientName,
            String productEan,   // optional – context only
            String appVersion,
            String platform,
            String notes
    ) {}

    /**
     * id              → DB primary key of ingredient_missing_report row
     * alreadyReported → true if this ingredientName was seen before (any productEan)
     */
    public record MissingIngredientReportResponse(
            Long id,
            boolean alreadyReported
    ) {}
}
