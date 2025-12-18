package app.goodbuy.adapters.core.users.repo;

import app.goodbuy.adapters.core.users.model.MagicLinkTokenEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

public interface MagicLinkTokenRepository extends JpaRepository<MagicLinkTokenEntity, UUID> {

    /**
     * Find a token by its opaque token string.
     */
    Optional<MagicLinkTokenEntity> findByToken(String token);

    /**
     * Optionally helpful later for cleanup jobs.
     */
    void deleteByExpiresAtBefore(OffsetDateTime cutoff);
}
