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
 *  - findByEmail / findByEmailIgnoreCase (unique)
 *  - existsByEmail / existsByEmailIgnoreCase
 *  - basic CRUD
 */
@Repository
public interface AppUserRepository extends JpaRepository<AppUserEntity, UUID> {

    // Legacy / exact match (keep if used elsewhere)
    Optional<AppUserEntity> findByEmail(String email);

    boolean existsByEmail(String email);

    // Case-insensitive variants used by AppUserService
    Optional<AppUserEntity> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);
}
