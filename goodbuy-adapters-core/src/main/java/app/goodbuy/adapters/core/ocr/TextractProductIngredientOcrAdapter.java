package app.goodbuy.adapters.core.ocr;

import app.goodbuy.core.products.port.ProductIngredientOcrPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.textract.TextractClient;
import software.amazon.awssdk.services.textract.model.Block;
import software.amazon.awssdk.services.textract.model.DetectDocumentTextRequest;
import software.amazon.awssdk.services.textract.model.DetectDocumentTextResponse;
import software.amazon.awssdk.services.textract.model.Document;
import software.amazon.awssdk.services.textract.model.TextractException;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

@Component
@ConditionalOnProperty(name = "goodbuy.ocr.provider", havingValue = "textract")
public class TextractProductIngredientOcrAdapter implements ProductIngredientOcrPort {

    private static final Logger log = LoggerFactory.getLogger(TextractProductIngredientOcrAdapter.class);

    private final TextractClient textractClient;

    public TextractProductIngredientOcrAdapter(TextractClient textractClient) {
        this.textractClient = textractClient;
    }

    @Override
    public Optional<ProductIngredientOcrResult> extract(ProductIngredientOcrRequest request) {
        if (request == null) {
            return Optional.empty();
        }

        List<String> sections = new ArrayList<>();

        String backText = extractText(request.productEan(), "back", request.backImageBytes());
        if (backText != null && !backText.isBlank()) {
            sections.add(backText);
        }

        String frontText = extractText(request.productEan(), "front", request.frontImageBytes());
        if (frontText != null && !frontText.isBlank()) {
            sections.add(frontText);
        }

        if (sections.isEmpty()) {
            return Optional.empty();
        }

        String rawText = mergeSections(sections);
        if (rawText.isBlank()) {
            return Optional.empty();
        }

        return Optional.of(new ProductIngredientOcrResult(
                rawText,
                "aws-textract",
                "textract_detect_document_text"
        ));
    }

    private String extractText(String ean, String side, byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return null;
        }

        try {
            DetectDocumentTextResponse response = textractClient.detectDocumentText(
                    DetectDocumentTextRequest.builder()
                            .document(Document.builder().bytes(SdkBytes.fromByteArray(bytes)).build())
                            .build()
            );

            String text = extractLines(response.blocks());
            log.info("TextractProductIngredientOcrAdapter: OCR success ean={} side={} chars={}",
                    ean, side, text.length());
            return text;
        } catch (TextractException ex) {
            log.warn("TextractProductIngredientOcrAdapter: OCR failed ean={} side={} msg={}",
                    ean, side, ex.awsErrorDetails() != null ? ex.awsErrorDetails().errorMessage() : ex.getMessage());
            return null;
        } catch (Exception ex) {
            log.warn("TextractProductIngredientOcrAdapter: OCR failed ean={} side={} type={} msg={}",
                    ean, side, ex.getClass().getSimpleName(), ex.getMessage());
            return null;
        }
    }

    static String extractLines(List<Block> blocks) {
        if (blocks == null || blocks.isEmpty()) {
            return "";
        }

        StringBuilder out = new StringBuilder();
        for (Block block : blocks) {
            if (block == null || block.blockType() == null) {
                continue;
            }
            if (!"LINE".equalsIgnoreCase(block.blockTypeAsString())) {
                continue;
            }
            String text = block.text();
            if (text == null || text.isBlank()) {
                continue;
            }
            if (out.length() > 0) {
                out.append('\n');
            }
            out.append(text.trim());
        }
        return out.toString();
    }

    private static String mergeSections(List<String> sections) {
        Set<String> uniqueLines = new LinkedHashSet<>();
        for (String section : sections) {
            if (section == null || section.isBlank()) {
                continue;
            }
            for (String rawLine : section.split("\\R")) {
                String line = normalizeLine(rawLine);
                if (!line.isBlank()) {
                    uniqueLines.add(line);
                }
            }
        }
        return String.join("\n", uniqueLines);
    }

    private static String normalizeLine(String value) {
        if (value == null) {
            return "";
        }
        String line = value.trim().replaceAll("\\s+", " ");
        String lower = line.toLowerCase(Locale.ROOT);
        if (lower.matches("^(supplement facts|nutrition facts|serving size|amount per serving).*$")) {
            return "";
        }
        return line;
    }
}
