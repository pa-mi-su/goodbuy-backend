package app.goodbuy.history;

import app.goodbuy.adapters.core.history.ScanHistoryMapper;
import app.goodbuy.adapters.core.history.model.ScanHistoryEntity;
import app.goodbuy.adapters.core.history.repo.ScanHistoryRepository;
import app.goodbuy.adapters.core.users.model.AppUserEntity;
import app.goodbuy.core.history.dto.ScanHistoryDTO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/history")
@Validated
public class HistoryController {

    private static final Logger log = LoggerFactory.getLogger(HistoryController.class);

    private static final String AUTH_USER_ATTR = "goodbuyUser";

    private final ScanHistoryRepository historyRepository;

    public HistoryController(ScanHistoryRepository historyRepository) {
        this.historyRepository = historyRepository;
    }

    // ─────────────────────────────────────
    // GET: list history (auth via X-Session-Token)
    // ─────────────────────────────────────

    @GetMapping
    public ResponseEntity<List<ScanHistoryDTO>> listHistory(HttpServletRequest request) {
        UUID userId = requireAuthenticatedUserId(request);

        List<ScanHistoryEntity> entities =
                historyRepository.findByUserIdOrderByScannedAtDesc(userId);

        if (entities.isEmpty()) {
            return ResponseEntity.noContent().build();
        }

        List<ScanHistoryDTO> dtos = entities.stream()
                .map(ScanHistoryMapper::toDTO)
                .toList();

        return ResponseEntity.ok(dtos);
    }

    // ─────────────────────────────────────
    // POST: record a scan (auth via X-Session-Token)
    // ─────────────────────────────────────
    //
    // POST /api/v1/history/scan
    // {
    //   "ean": "...",
    //   "productName": "...",
    //   "brand": "..."
    // }
    //
    // 201 Created (new pair) or 200 OK (existing user+ean), no body.

    @PostMapping("/scan")
    public ResponseEntity<Void> recordScan(
            @Valid @RequestBody RecordScanRequest requestBody,
            HttpServletRequest request
    ) {
        UUID userId = requireAuthenticatedUserId(request);

        String ean = requestBody.ean().trim();
        Optional<ScanHistoryEntity> existingOpt =
                historyRepository.findByUserIdAndEan(userId, ean);

        ScanHistoryEntity entity;
        HttpStatus status;

        if (existingOpt.isPresent()) {
            entity = existingOpt.get();
            status = HttpStatus.OK;

            boolean changed = false;
            if (isBlank(entity.getProductName()) && !isBlank(requestBody.productName())) {
                entity.setProductName(requestBody.productName());
                changed = true;
            }
            if (isBlank(entity.getBrand()) && !isBlank(requestBody.brand())) {
                entity.setBrand(requestBody.brand());
                changed = true;
            }

            if (changed) {
                historyRepository.save(entity);
            }

            log.info("HistoryController.recordScan: ignored repeat scan userId={} ean={} changedMetadata={}",
                    userId, ean, changed);
            return ResponseEntity.status(status).build();
        } else {
            entity = new ScanHistoryEntity(userId, ean);
            status = HttpStatus.CREATED;
            entity.setProductName(requestBody.productName());
            entity.setBrand(requestBody.brand());
            entity.setScannedAt(OffsetDateTime.now());
            historyRepository.save(entity);
            log.info("HistoryController.recordScan: recorded new scan userId={} ean={}", userId, ean);
            return ResponseEntity.status(status).build();
        }
    }

    // ─────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────

    private static UUID requireAuthenticatedUserId(HttpServletRequest request) {
        Object obj = request.getAttribute(AUTH_USER_ATTR);
        if (obj instanceof AppUserEntity user) {
            return user.getId();
        }
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Valid session token is required");
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    // ─────────────────────────────────────
    // DTO
    // ─────────────────────────────────────

    public record RecordScanRequest(
            @NotBlank String ean,
            String productName,
            String brand
    ) {}
}
