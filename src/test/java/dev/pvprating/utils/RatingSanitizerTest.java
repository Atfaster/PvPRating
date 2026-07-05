package dev.pvprating.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class RatingSanitizerTest {
    @Test
    void rejectsNonFiniteRatingValues() {
        assertNull(RatingSanitizer.sanitize(Double.NaN, -100.0, 100.0));
        assertNull(RatingSanitizer.sanitize(Double.POSITIVE_INFINITY, -100.0, 100.0));
        assertNull(RatingSanitizer.sanitize(Double.NEGATIVE_INFINITY, -100.0, 100.0));
    }

    @Test
    void keepsFiniteRatingInsideBounds() {
        assertEquals(25.5, RatingSanitizer.sanitize(25.5, -100.0, 100.0));
    }

    @Test
    void clampsFiniteRatingToBounds() {
        assertEquals(100.0, RatingSanitizer.sanitize(150.0, -100.0, 100.0));
        assertEquals(-100.0, RatingSanitizer.sanitize(-150.0, -100.0, 100.0));
    }

    @Test
    void handlesReversedBoundsDefensively() {
        assertEquals(100.0, RatingSanitizer.sanitize(150.0, 100.0, -100.0));
        assertEquals(-100.0, RatingSanitizer.sanitize(-150.0, 100.0, -100.0));
    }

    @Test
    void ignoresNonFiniteBounds() {
        assertEquals(Double.MAX_VALUE, RatingSanitizer.sanitize(Double.MAX_VALUE, Double.NaN, Double.POSITIVE_INFINITY));
        assertEquals(-Double.MAX_VALUE, RatingSanitizer.sanitize(-Double.MAX_VALUE, Double.NEGATIVE_INFINITY, Double.NaN));
    }
}
