package app.goodbuy.adapters.core.citations.service;

import app.goodbuy.adapters.core.citations.model.CitationEntity;
import app.goodbuy.adapters.core.citations.model.IngredientCitationEntity;
import app.goodbuy.adapters.core.citations.model.SourceEntity;
import app.goodbuy.adapters.core.citations.repo.CitationRepository;
import app.goodbuy.adapters.core.citations.repo.IngredientCitationRepository;
import app.goodbuy.adapters.core.citations.repo.SourceRepository;
import app.goodbuy.adapters.core.ingredients.model.Ingredient;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

@Service
public class IngredientCitationWriter {

    private static final Logger log = LoggerFactory.getLogger(IngredientCitationWriter.class);

    private final SourceRepository sourceRepo;
    private final CitationRepository citationRepo;
    private final IngredientCitationRepository joinRepo;

    @PersistenceContext
    private EntityManager em;

    public IngredientCitationWriter(
            SourceRepository sourceRepo,
            CitationRepository citationRepo,
            IngredientCitationRepository joinRepo
    ) {
        this.sourceRepo = sourceRepo;
        this.citationRepo = citationRepo;
        this.joinRepo = joinRepo;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void attachCitations(Long ingredientId, String sourceName, List<String> urls, String citationTitleOrNull) {
        if (ingredientId == null) return;
        if (urls == null || urls.isEmpty()) return;

        String normalizedSource = (sourceName == null || sourceName.isBlank())
                ? "Unknown"
                : sourceName.trim();

        SourceEntity source = sourceRepo.findByNameIgnoreCase(normalizedSource).orElse(null);

        Ingredient ingredientRef = em.getReference(Ingredient.class, ingredientId);

        int attached = 0;

        for (String url : urls) {
            if (url == null) continue;
            String u = url.trim();
            if (u.isBlank()) continue;

            CitationEntity citation = citationRepo.findByUrl(u).orElseGet(() -> createCitation(u, source, citationTitleOrNull));

            if (joinRepo.existsByIngredientIdAndCitationId(ingredientId, citation.getId())) {
                continue;
            }

            IngredientCitationEntity join = new IngredientCitationEntity();
            join.setIngredient(ingredientRef);
            join.setCitation(em.getReference(CitationEntity.class, citation.getId()));
            try {
                joinRepo.saveAndFlush(join);
            } catch (DataIntegrityViolationException ex) {
                if (joinRepo.existsByIngredientIdAndCitationId(ingredientId, citation.getId())) {
                    continue;
                }
                throw ex;
            }

            attached++;
        }

        if (attached > 0) {
            log.info("IngredientCitationWriter: attached {} citations -> ingredientId={} source={}",
                    attached, ingredientId, source == null ? "Unknown" : source.getName());
        }
    }

    private CitationEntity createCitation(String url, SourceEntity source, String citationTitleOrNull) {
        CitationEntity citation = new CitationEntity();
        citation.setUrl(url);
        citation.setSource(source);
        citation.setTitle((citationTitleOrNull == null || citationTitleOrNull.isBlank()) ? null : citationTitleOrNull.trim());
        citation.setAccessedAt(OffsetDateTime.now());
        try {
            return citationRepo.saveAndFlush(citation);
        } catch (DataIntegrityViolationException ex) {
            return citationRepo.findByUrl(url).orElseThrow(() -> ex);
        }
    }
}
