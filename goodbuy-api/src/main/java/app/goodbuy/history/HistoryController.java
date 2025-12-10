package app.goodbuy.history;

import app.goodbuy.adapters.core.history.ScanHistoryMapper;
import app.goodbuy.adapters.core.history.model.ScanHistoryEntity;
import app.goodbuy.adapters.core.history.repo.ScanHistoryRepository;
import app.goodbuy.adapters.core.users.service.AppUserService;
import app.goodbuy.core.history.dto.ScanHistoryDTO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/history")
@Validated
public class HistoryController {

    private static final Logger log = LoggerFactory.getLogger(HistoryController.class);

    private final ScanHistoryRepository historyRepository;
    private final AppUserService appUserService;

    public HistoryController(ScanHistoryRepository historyRepository,
                             AppUserService appUserService) {
        this.historyRepository = historyRepository;
        this.appUserService = appUserService;
    }

    // ─────────────────────────────────────
    // GET: list history
    // ─────────────────────────────────────

    @GetMapping
    public ResponseEntity<List<ScanHistoryDTO>> listHistory(
            @RequestParam("userId") @NotBlank String userIdRaw
    ) {
        UUID userId;
        try {
            userId = UUID.fromString(userIdRaw);
        } catch (IllegalArgumentException ex) {
            log.warn("HistoryController.listHistory invalid userId='{}'", userIdRaw);
            return ResponseEntity.badRequest().build();
        }

        List<ScanHistoryEntity> entities =
                historyRepository.findByUserIdOrderByScannedAtDesc(userId);

        if (entities.isEmpty()) {
            return ResponseEntity.noContent().build(); // 204 → app shows “no scans yet”
        }

        List<ScanHistoryDTO> dtos = entities.stream()
                .map(ScanHistoryMapper::toDTO)
                .toList();

        return ResponseEntity.ok(dtos);
    }

    // ─────────────────────────────────────
    // POST: record a scan
    // ─────────────────────────────────────
    //
    // POST /api/v1/history/scan
    // {
    //   "userId": "9630-...-4500",
    //   "ean": "00817939000052",
    //   "productName": "Method All-Purpose Cleaner...",
    //   "brand": "Method"
    // }
    //
    // 201 Created (new pair) or 200 OK (existing user+ean), no body.

    @PostMapping("/scan")
    public ResponseEntity<Void> recordScan(
            @Valid @RequestBody RecordScanRequest request
    ) {
        UUID userId;
        try {
            userId = UUID.fromString(request.userId());
        } catch (IllegalArgumentException ex) {
            log.warn("HistoryController.recordScan invalid userId='{}'", request.userId());
            return ResponseEntity.badRequest().build();
        }

        // Ensure this user actually exists. If not, treat as client error:
        // the app should have called /api/v1/users/register first.
        try {
            appUserService.ensureUserExistsById(userId, null, null);
        } catch (IllegalArgumentException ex) {
            log.warn("HistoryController.recordScan: user not found for id={} → 400", userId);
            return ResponseEntity.badRequest().build();
        } catch (Exception ex) {
            log.error("HistoryController.recordScan: failed to check app_user for id={}", userId, ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }

        var now = OffsetDateTime.now();

        // Upsert on (userId, ean) to respect ux_scan_history_user_ean
        Optional<ScanHistoryEntity> existingOpt =
                historyRepository.findByUserIdAndEan(userId, request.ean());

        ScanHistoryEntity entity;
        HttpStatus status;

        if (existingOpt.isPresent()) {
            entity = existingOpt.get();
            status = HttpStatus.OK;
        } else {
            entity = new ScanHistoryEntity(userId, request.ean());
            status = HttpStatus.CREATED;
        }

        entity.setProductName(request.productName());
        entity.setBrand(request.brand());
        entity.setScannedAt(now);

        historyRepository.save(entity);

        return ResponseEntity.status(status).build();
    }

    // ─────────────────────────────────────
    // DTO
    // ─────────────────────────────────────

    public record RecordScanRequest(
            @NotBlank String userId,
            @NotBlank String ean,
            String productName,
            String brand
    ) {}
}
