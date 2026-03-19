package app.goodbuy.ingredients;

import app.goodbuy.core.ingredients.port.IngredientEnrichmentQueuePort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AsyncIngredientResearchService implements IngredientEnrichmentQueuePort {

    private static final Logger log = LoggerFactory.getLogger(AsyncIngredientResearchService.class);

    private final AsyncIngredientResearchWorker worker;
    private final Set<Long> inFlightIngredientIds = ConcurrentHashMap.newKeySet();

    public AsyncIngredientResearchService(AsyncIngredientResearchWorker worker) {
        this.worker = worker;
    }

    @Override
    public void enqueue(long ingredientId, String reason) {
        if (ingredientId <= 0) {
            return;
        }

        if (!inFlightIngredientIds.add(ingredientId)) {
            log.info("AsyncIngredientResearchService: research already in flight ingredientId={} reason={}",
                    ingredientId, safe(reason));
            return;
        }

        worker.researchAsync(ingredientId, safe(reason), () -> inFlightIngredientIds.remove(ingredientId));
    }

    private static String safe(String value) {
        return (value == null || value.isBlank()) ? "-" : value.trim();
    }
}
