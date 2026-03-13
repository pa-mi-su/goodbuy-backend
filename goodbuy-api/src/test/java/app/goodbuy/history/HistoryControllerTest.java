package app.goodbuy.history;

import app.goodbuy.adapters.core.history.model.ScanHistoryEntity;
import app.goodbuy.adapters.core.history.repo.ScanHistoryRepository;
import app.goodbuy.adapters.core.users.model.AppUserEntity;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HistoryControllerTest {

    @Test
    void recordScanNormalizesEanBeforeLookupAndSave() {
        ScanHistoryRepository repository = mock(ScanHistoryRepository.class);
        HttpServletRequest request = mock(HttpServletRequest.class);
        AppUserEntity user = new AppUserEntity();
        UUID userId = UUID.randomUUID();
        user.setId(userId);

        when(request.getAttribute("goodbuyUser")).thenReturn(user);
        when(repository.findByUserIdAndEan(userId, "00016500586579")).thenReturn(Optional.empty());
        when(repository.save(any(ScanHistoryEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        HistoryController controller = new HistoryController(repository);

        var response = controller.recordScan(
                new HistoryController.RecordScanRequest("0016500586579", "One A Day", "One A Day"),
                request
        );

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        verify(repository).findByUserIdAndEan(userId, "00016500586579");
    }
}
