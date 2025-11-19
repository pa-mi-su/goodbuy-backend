package app.goodbuy.api.ingredients;

import app.goodbuy.adapters.core.ingredients.model.MissingIngredientReportEntity;
import app.goodbuy.adapters.core.ingredients.service.MissingIngredientReportService;
import jakarta.validation.constraints.NotBlank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

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
        MissingIngredientReportEntity saved = service.report(
                req.ingredientName(),
                req.productEan(),
                req.appVersion(),
                req.platform(),
                req.notes()
        );

        log.info("Missing ingredient reported ingredient='{}' ean={} platform={} version={}",
                req.ingredientName(), req.productEan(), req.platform(), req.appVersion());

        return new MissingIngredientReportResponse(saved.getId());
    }

    // ─────────────────────────────────────────────────────────────
    // Request + Response DTOs
    // ─────────────────────────────────────────────────────────────

    public record MissingIngredientReportRequest(
            @NotBlank String ingredientName,
            String productEan,
            String appVersion,
            String platform,
            String notes
    ) {}

    public record MissingIngredientReportResponse(Long id) {}
}
