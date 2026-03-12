package app.goodbuy.products;

import app.goodbuy.core.products.StrictProductIngestionException;
import app.goodbuy.core.products.dto.ProductDetailDto;
import app.goodbuy.core.products.port.ProductSnapshotPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class AsyncProductIngestionWorker {

    private static final Logger log = LoggerFactory.getLogger(AsyncProductIngestionWorker.class);

    private final ProductSnapshotPort snapshot;

    public AsyncProductIngestionWorker(Optional<ProductSnapshotPort> snapshot) {
        this.snapshot = snapshot.orElse(null);
    }

    @Async
    public void ingestAsync(ProductDetailDto dto, String gtin, Runnable onComplete) {
        try {
            if (dto == null || snapshot == null) {
                return;
            }

            log.info("AsyncProductIngestionWorker: begin async ingestion gtin={} name={} brand={}",
                    gtin, safe(dto.name()), safe(dto.brand()));
            snapshot.saveSnapshot(dto);
            log.info("AsyncProductIngestionWorker: async ingestion complete gtin={}", gtin);
        } catch (StrictProductIngestionException ex) {
            log.warn("AsyncProductIngestionWorker: strict ingestion incomplete gtin={} msg={}", gtin, ex.getMessage());
        } catch (Exception ex) {
            log.warn("AsyncProductIngestionWorker: ingestion failed gtin={} type={} msg={}",
                    gtin, ex.getClass().getSimpleName(), ex.getMessage(), ex);
        } finally {
            if (onComplete != null) {
                onComplete.run();
            }
        }
    }

    private static String safe(String s) {
        return (s == null || s.isBlank()) ? "-" : s;
    }
}
