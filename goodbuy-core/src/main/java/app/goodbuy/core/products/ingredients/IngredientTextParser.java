package app.goodbuy.core.products.ingredients;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class IngredientTextParser {

    private IngredientTextParser() {}

    public static List<String> parse(String rawText) {
        if (rawText == null || rawText.isBlank()) {
            return List.of();
        }

        String normalized = normalizeSourceText(rawText);
        if (normalized.isBlank()) {
            return List.of();
        }

        List<String> tokens = splitTopLevel(normalized);
        if (tokens.isEmpty()) {
            return List.of();
        }

        Set<String> unique = new LinkedHashSet<>();
        for (String token : tokens) {
            for (String expanded : expandToken(token)) {
                String cleaned = cleanToken(expanded);
                if (!cleaned.isBlank() && !isHeaderOnly(cleaned)) {
                    unique.add(cleaned);
                }
            }
        }

        return List.copyOf(unique);
    }

    private static String normalizeSourceText(String rawText) {
        String normalized = rawText
                .replace('\n', ',')
                .replace('\r', ',')
                .replace('\u2022', ',')
                .replace('\u2023', ',')
                .replace('\u25E6', ',')
                .replace('\u2043', ',');

        normalized = normalized.replaceFirst("(?i)^\\s*ingredients\\s*:\\s*", "");
        normalized = normalized.replaceFirst("(?i)^\\s*other\\s+ingredients\\s*:\\s*", "");
        normalized = normalized.replaceFirst("(?i)^\\s*inactive\\s+ingredients\\s*:\\s*", "");

        return normalized.trim();
    }

    private static List<String> splitTopLevel(String raw) {
        List<String> out = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int depth = 0;

        for (int i = 0; i < raw.length(); i++) {
            char ch = raw.charAt(i);
            if (ch == '(' || ch == '[' || ch == '{') {
                depth++;
                current.append(ch);
                continue;
            }
            if (ch == ')' || ch == ']' || ch == '}') {
                depth = Math.max(0, depth - 1);
                current.append(ch);
                continue;
            }

            if ((ch == ',' || ch == ';') && depth == 0) {
                addToken(out, current);
                current.setLength(0);
                continue;
            }

            current.append(ch);
        }

        addToken(out, current);
        return out;
    }

    private static void addToken(List<String> out, StringBuilder current) {
        String token = current.toString().trim();
        if (!token.isBlank()) {
            out.add(token);
        }
    }

    private static List<String> expandToken(String token) {
        String trimmed = token == null ? "" : token.trim();
        if (trimmed.isBlank()) {
            return List.of();
        }

        if (isHeaderOnly(trimmed)) {
            return List.of();
        }

        String stripped = stripLessThanPrefix(trimmed);
        if (!stripped.equals(trimmed)) {
            return splitTopLevel(stripped);
        }

        return List.of(trimmed);
    }

    private static boolean isHeaderOnly(String value) {
        String lower = value.trim().toLowerCase(Locale.ROOT);
        return lower.matches("^less\\s+than\\s*\\d+\\s*%\\s+of\\s*:?\\s*$")
                || lower.matches("^contains\\s+less\\s+than\\s*\\d+\\s*%\\s+of\\s*:?\\s*$")
                || lower.matches("^<\\s*\\d+\\s*%\\s+of\\s*:?\\s*$");
    }

    private static String stripLessThanPrefix(String value) {
        return value
                .replaceFirst("(?i)^contains\\s+less\\s+than\\s*\\d+\\s*%\\s+of\\s*:?\\s*", "")
                .replaceFirst("(?i)^less\\s+than\\s*\\d+\\s*%\\s+of\\s*:?\\s*", "")
                .replaceFirst("(?i)^<\\s*\\d+\\s*%\\s+of\\s*:?\\s*", "")
                .trim();
    }

    private static String cleanToken(String token) {
        String cleaned = token == null ? "" : token.trim();
        cleaned = cleaned.replaceAll("\\s+", " ");
        cleaned = cleaned.replaceAll("^[,:\\-\\s]+", "");
        cleaned = cleaned.replaceAll("[,;:.\\s]+$", "");
        return cleaned.trim();
    }
}
