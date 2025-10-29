package app.goodbuy.products;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class BarcodeNormalizer {

    /**
     * Normalize various retail barcodes (EAN-8, UPC-A, EAN-13, GTIN-14)
     * to a canonical 14-digit GTIN-14 string.
     * Throws 422 (invalid_barcode) when input is malformed or checksum fails.
     */
    public String normalizeToGtin14OrThrow(String raw) {
        if (raw == null) {
            invalid("null");
        }

        // Keep only digits
        String digits = raw.replaceAll("\\D", "");
        if (digits.isEmpty()) {
            invalid("empty");
        }

        // Accept only 8, 12, 13, or 14 digits (UPC-E expansion not handled in this minimal version)
        int len = digits.length();
        if (!(len == 8 || len == 12 || len == 13 || len == 14)) {
            invalid("length " + len);
        }

        // Verify GS1 check digit (last digit) for provided code
        if (!hasValidGs1CheckDigit(digits)) {
            invalid("checksum");
        }

        // Left-pad to 14 digits for canonical GTIN-14
        return String.format("%014d", Long.parseLong(digits));
    }

    // GS1 (EAN/UPC/GTIN) check digit:
    // Sum weights from right (excluding check digit): 3,1,3,1...
    // check = (10 - (sum % 10)) % 10
    private boolean hasValidGs1CheckDigit(String code) {
        int n = code.length();
        int check = Character.digit(code.charAt(n - 1), 10);
        int sum = 0;
        // process right-to-left, excluding check digit
        for (int i = n - 2, posFromRight = 1; i >= 0; i--, posFromRight++) {
            int d = Character.digit(code.charAt(i), 10);
            // positions alternate 3x and 1x weight starting with 3 nearest the check digit
            sum += d * ((posFromRight % 2 == 1) ? 3 : 1);
        }
        int expected = (10 - (sum % 10)) % 10;
        return expected == check;
    }

    private void invalid(String reason) {
        throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "invalid_barcode: " + reason);
    }
}
