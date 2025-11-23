package app.goodbuy.adapters.core.users.repo;

import app.goodbuy.adapters.core.users.model.AppUserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository for app_user table.
 *
 * Supports:
 *  - findByEmail (unique)
 *  - save
 *  - existsByEmail
 */
@Repository
public interface AppUserRepository extends JpaRepository<AppUserEntity, UUID> {

    Optional<AppUserEntity> findByEmail(String email);

    boolean existsByEmail(String email);
}
