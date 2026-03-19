package app.goodbuy.ingredients;

import app.goodbuy.adapters.core.ingredients.service.MissingIngredientReportService;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/ingredients/missing")
public class MissingIngredientReportController {

    private final MissingIngredientReportService service;
    private final IngredientOnDemandResearchService ingredientOnDemandResearchService;

    public MissingIngredientReportController(
            MissingIngredientReportService service,
            IngredientOnDemandResearchService ingredientOnDemandResearchService
    ) {
        this.service = service;
        this.ingredientOnDemandResearchService = ingredientOnDemandResearchService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public MissingIngredientReportResponse report(
            @RequestBody MissingIngredientReportRequest req
    ) {
        var result = service.reportWithStatus(
                req.ingredientName(),
                req.productEan(),
                req.appVersion(),
                req.platform(),
                req.notes()
        );
        ingredientOnDemandResearchService.getOrStartResearch(req.ingredientName());

        return new MissingIngredientReportResponse(
                result.entity().getId(),
                !result.isNew()
        );
    }

    // ─────────────────────────────

    public record MissingIngredientReportRequest(
            @NotBlank String ingredientName,
            String productEan,
            String appVersion,
            String platform,
            String notes
    ) {}

    public record MissingIngredientReportResponse(
            Long id,
            boolean alreadyReported
    ) {}
}
