package app.goodbuy.api.admin;

import app.goodbuy.adapters.core.ingredients.scoring.IngredientScoringAdapterService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Admin-only endpoints for ingredient maintenance and scoring.
 *
 * NOTE: Right now this is wide-open. Once you add auth/roles, you probably want
 * to secure this under an admin role or at least behind a feature flag.
 */
@RestController
@RequestMapping("/api/v1/admin/ingredients")
public class IngredientAdminController {

    private static final Logger log = LoggerFactory.getLogger(IngredientAdminController.class);

    private final IngredientScoringAdapterService scoringAdapterService;
    private final IngredientReviewAdminService ingredientReviewAdminService;

    public IngredientAdminController(
            IngredientScoringAdapterService scoringAdapterService,
            IngredientReviewAdminService ingredientReviewAdminService
    ) {
        this.scoringAdapterService = scoringAdapterService;
        this.ingredientReviewAdminService = ingredientReviewAdminService;
    }

    /**
     * Recalculate safety scores + rating letters for ALL ingredients.
     *
     * Usage (dev):
     *   curl -X POST http://localhost:8080/api/v1/admin/ingredients/recalc-scores
     */
    @PostMapping("/recalc-scores")
    public ResponseEntity<RecalcScoresResponse> recalcAllScores() {
        log.info("IngredientAdminController: starting full ingredient rescoring run…");

        int updated = scoringAdapterService.recalcScoresForAllIngredients();

        log.info("IngredientAdminController: completed rescoring; updated {} ingredients", updated);

        RecalcScoresResponse body = new RecalcScoresResponse(updated);
        return ResponseEntity.ok(body);
    }

    @GetMapping("/missing")
    public ResponseEntity<List<IngredientReviewAdminService.MissingIngredientQueueItem>> listMissingQueue(
            @RequestParam(defaultValue = "100") int limit
    ) {
        return ResponseEntity.ok(ingredientReviewAdminService.listOpenQueue(limit));
    }

    @PostMapping("/missing/resolve")
    public ResponseEntity<ResolveMissingIngredientResponse> resolveMissingIngredient(
            @RequestBody ResolveMissingIngredientRequest request
    ) {
        var result = ingredientReviewAdminService.resolveMissingIngredient(
                request.missingName(),
                request.canonicalKey(),
                request.displayName()
        );

        return ResponseEntity.ok(new ResolveMissingIngredientResponse(
                result.canonicalKey(),
                result.resolvedReports(),
                result.reprocessedProducts()
        ));
    }

    /**
     * Simple response DTO so the frontend (or you via curl) gets a nice payload.
     */
    public static class RecalcScoresResponse {
        private final int updatedCount;

        public RecalcScoresResponse(int updatedCount) {
            this.updatedCount = updatedCount;
        }

        public int getUpdatedCount() {
            return updatedCount;
        }
    }

    public record ResolveMissingIngredientRequest(
            String missingName,
            String canonicalKey,
            String displayName
    ) {}

    public record ResolveMissingIngredientResponse(
            String canonicalKey,
            int resolvedReports,
            int reprocessedProducts
    ) {}
}
