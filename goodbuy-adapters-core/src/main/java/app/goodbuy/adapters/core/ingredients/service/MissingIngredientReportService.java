package app.goodbuy.adapters.core.ingredients.service;

import app.goodbuy.adapters.core.ingredients.model.IngredientMissingReportEntity;
import app.goodbuy.adapters.core.ingredients.repository.IngredientMissingReportRepository;
import app.goodbuy.adapters.core.notifications.SlackNotificationAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class MissingIngredientReportService {

    private static final Logger log = LoggerFactory.getLogger(MissingIngredientReportService.class);

    private final IngredientMissingReportRepository repo;
    private final SlackNotificationAdapter slack;

    public record MissingIngredientReportResult(
            IngredientMissingReportEntity entity,
            boolean isNew
    ) {}

    public MissingIngredientReportService(
            IngredientMissingReportRepository repo,
            SlackNotificationAdapter slack
    ) {
        this.repo = repo;
        this.slack = slack;
    }

    @Transactional
    public MissingIngredientReportResult reportWithStatus(
            String ingredientName,
            String productEan,
            String appVersion,
            String platform,
            String notes
    ) {
        Instant now = Instant.now();
        boolean isNew = repo.insertIfAbsent(
                ingredientName,
                productEan,
                appVersion,
                platform,
                notes,
                now,
                now
        ) > 0;

        if (!isNew) {
            repo.touchExisting(
                    ingredientName,
                    productEan,
                    appVersion,
                    platform,
                    notes,
                    now
            );
        }

        IngredientMissingReportEntity entity = repo.findByIngredientNameIgnoreCaseAndProductEan(ingredientName, productEan)
                .orElseThrow(() -> new IllegalStateException(
                        "Missing ingredient report row unavailable after upsert for ingredient='"
                                + ingredientName + "' ean=" + productEan
                ));

        if (isNew) {
            String slackText = """
                    🧪 Missing ingredient detected

                    • Ingredient: %s
                    • Product EAN: %s
                    • Platform: %s
                    • App Version: %s

                    Notes:
                    %s
                    """.formatted(
                    ingredientName,
                    productEan,
                    platform,
                    appVersion,
                    (notes == null || notes.isBlank()) ? "(none)" : notes
            );

            slack.sendIngredientMissing(slackText);
        }

        log.info(
                "MissingIngredientReportService.reportWithStatus: ingredient='{}' ean={} isNew={}",
                ingredientName, productEan, isNew
        );

        return new MissingIngredientReportResult(entity, isNew);
    }
}
