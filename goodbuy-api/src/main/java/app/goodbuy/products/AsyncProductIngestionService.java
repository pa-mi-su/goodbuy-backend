package app.goodbuy.products;

import app.goodbuy.core.products.dto.ProductDetailDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AsyncProductIngestionService {

    private static final Logger log = LoggerFactory.getLogger(AsyncProductIngestionService.class);

    private final AsyncProductIngestionWorker worker;
    private final Set<String> inFlightGtins = ConcurrentHashMap.newKeySet();

    public AsyncProductIngestionService(AsyncProductIngestionWorker worker) {
        this.worker = worker;
    }

    public void enqueue(ProductDetailDto dto) {
        if (dto == null) {
            return;
        }

        String gtin = normalize(dto.gtin());
        if (gtin == null) {
            return;
        }

        if (!inFlightGtins.add(gtin)) {
            log.info("AsyncProductIngestionService: ingestion already in flight gtin={}", gtin);
            return;
        }

        worker.ingestAsync(dto, gtin, () -> inFlightGtins.remove(gtin));
    }

    private static String normalize(String gtin) {
        if (gtin == null) return null;
        String trimmed = gtin.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

}
