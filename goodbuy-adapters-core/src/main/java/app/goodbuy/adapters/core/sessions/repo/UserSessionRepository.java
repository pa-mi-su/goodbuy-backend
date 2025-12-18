package app.goodbuy.adapters.core.sessions.repo;

import app.goodbuy.adapters.core.sessions.model.UserSessionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserSessionRepository extends JpaRepository<UserSessionEntity, UUID> {

    Optional<UserSessionEntity> findByToken(String token);

    long deleteByUserId(UUID userId);
}
