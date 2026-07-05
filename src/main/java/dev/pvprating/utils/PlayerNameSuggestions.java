package dev.pvprating.utils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

public class PlayerNameSuggestions {
    public static List<String> filter(Collection<String> names, String prefix, int limit) {
        if (names == null || limit <= 0) return List.of();

        String lowerPrefix = prefix == null ? "" : prefix.toLowerCase(Locale.ROOT);
        List<String> suggestions = new ArrayList<>();
        for (String name : names) {
            if (name == null || name.isBlank()) continue;
            if (lowerPrefix.isEmpty() || name.toLowerCase(Locale.ROOT).startsWith(lowerPrefix)) {
                suggestions.add(name);
            }
        }

        suggestions.sort(String.CASE_INSENSITIVE_ORDER);
        if (suggestions.size() > limit) return new ArrayList<>(suggestions.subList(0, limit));
        return suggestions;
    }
}
