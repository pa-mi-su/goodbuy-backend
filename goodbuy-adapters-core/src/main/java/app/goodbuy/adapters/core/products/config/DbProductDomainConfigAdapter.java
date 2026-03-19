package app.goodbuy.adapters.core.products.config;

import app.goodbuy.adapters.core.products.model.ProductDomainConfigEntity;
import app.goodbuy.adapters.core.products.repo.ProductDomainConfigRepository;
import app.goodbuy.core.products.port.ProductDomainConfigPort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Component
public class DbProductDomainConfigAdapter implements ProductDomainConfigPort {

    private final ProductDomainConfigRepository repo;

    public DbProductDomainConfigAdapter(ProductDomainConfigRepository repo) {
        this.repo = repo;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isEnabled(String domain) {
        String key = normalizeDomainKey(domain);
        if (key == null) return false;

        return repo.findById(key)
                .map(ProductDomainConfigEntity::isEnabled)
                .orElse(false);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isRated(String domain) {
        String key = normalizeDomainKey(domain);
        if (key == null) return false;

        return repo.findById(key)
                .map(ProductDomainConfigEntity::isRated)
                .orElse(false);
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> enabledDomains() {
        // DB is source of truth. Return canonical lowercase domain codes.
        return repo.findAll().stream()
                .filter(ProductDomainConfigEntity::isEnabled)
                .map(ProductDomainConfigEntity::getDomain) // assumes entity field is the PK column (e.g. "vitamins")
                .filter(s -> s != null && !s.isBlank())
                .map(s -> s.trim().toLowerCase(Locale.ROOT))
                .distinct()
                .sorted(Comparator.naturalOrder())
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> ratedDomains() {
        return repo.findAll().stream()
                .filter(ProductDomainConfigEntity::isRated)
                .map(ProductDomainConfigEntity::getDomain)
                .filter(s -> s != null && !s.isBlank())
                .map(s -> s.trim().toLowerCase(Locale.ROOT))
                .distinct()
                .sorted(Comparator.naturalOrder())
                .toList();
    }

    /**
     * Option A: DB is the source of truth.
     * The DB primary key is the canonical domain code, stored lowercase:
     *   vitamins, cleaning, baby, food, other, unknown
     *
     * Therefore: normalize caller input to lowercase and do a direct lookup.
     * NO hard-coded domain mapping here.
     */
    private static String normalizeDomainKey(String raw) {
        if (raw == null) return null;
        String s = raw.trim();
        if (s.isEmpty()) return null;

        return s.toLowerCase(Locale.ROOT);
    }
}
