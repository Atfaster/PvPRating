package dev.pvprating.utils;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlayerNameSuggestionsTest {
    @Test
    void filtersCaseInsensitivePrefixAndSortsNames() {
        List<String> suggestions = PlayerNameSuggestions.filter(
                List.of("zeta", "Alpha", "albert", "Bravo"),
                "al",
                10
        );

        assertEquals(List.of("albert", "Alpha"), suggestions);
    }

    @Test
    void limitsReturnedSuggestions() {
        List<String> suggestions = PlayerNameSuggestions.filter(
                List.of("Delta", "Charlie", "Bravo", "Alpha"),
                "",
                2
        );

        assertEquals(List.of("Alpha", "Bravo"), suggestions);
    }

    @Test
    void ignoresNullAndBlankNames() {
        List<String> suggestions = PlayerNameSuggestions.filter(
                java.util.Arrays.asList("Alex", null, "", "   ", "Alice"),
                "A",
                10
        );

        assertEquals(List.of("Alex", "Alice"), suggestions);
    }

    @Test
    void returnsEmptyListForNonPositiveLimit() {
        List<String> suggestions = PlayerNameSuggestions.filter(
                List.of("Alpha"),
                "",
                0
        );

        assertEquals(List.of(), suggestions);
    }
}
