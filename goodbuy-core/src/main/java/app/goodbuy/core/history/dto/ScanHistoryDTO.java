package app.goodbuy.core.history.dto;

import java.time.OffsetDateTime;

public record ScanHistoryDTO(
        long id,
        String ean,
        String productName,
        String brand,
        OffsetDateTime scannedAt
) {}
