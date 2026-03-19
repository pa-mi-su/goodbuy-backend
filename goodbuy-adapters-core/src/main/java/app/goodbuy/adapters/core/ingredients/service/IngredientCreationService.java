package app.goodbuy.adapters.core.ingredients.service;

import app.goodbuy.adapters.core.ingredients.model.Ingredient;
import app.goodbuy.adapters.core.ingredients.repository.IngredientRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IngredientCreationService {

    private final IngredientRepository ingredientRepo;

    public IngredientCreationService(IngredientRepository ingredientRepo) {
        this.ingredientRepo = ingredientRepo;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long createIngredient(Ingredient ingredient) {
        return ingredientRepo.saveAndFlush(ingredient).getId();
    }
}
