package app.goodbuy.adapters.core.ocr;

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.textract.model.Block;
import software.amazon.awssdk.services.textract.model.BlockType;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TextractProductIngredientOcrAdapterTest {

    @Test
    void extractsOnlyLineBlocks() {
        String text = TextractProductIngredientOcrAdapter.extractLines(List.of(
                Block.builder().blockType(BlockType.LINE).text("Ingredients: Water, Glycerin").build(),
                Block.builder().blockType(BlockType.WORD).text("ignored").build(),
                Block.builder().blockType(BlockType.LINE).text("Less than 2% of: Citric Acid").build()
        ));

        assertEquals("Ingredients: Water, Glycerin\nLess than 2% of: Citric Acid", text);
    }
}
