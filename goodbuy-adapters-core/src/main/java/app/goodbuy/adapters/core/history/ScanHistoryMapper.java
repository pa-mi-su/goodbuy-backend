package app.goodbuy.adapters.core.history;

import app.goodbuy.adapters.core.history.model.ScanHistoryEntity;
import app.goodbuy.core.history.dto.ScanHistoryDTO;

public class ScanHistoryMapper {

    public static ScanHistoryDTO toDTO(ScanHistoryEntity e) {
        return new ScanHistoryDTO(
                e.getId() != null ? e.getId() : 0L,
                e.getEan(),
                e.getProductName(),
                e.getBrand(),
                e.getScannedAt()
        );
    }
}
