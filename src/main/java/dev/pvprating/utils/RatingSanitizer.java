package dev.pvprating.utils;

public class RatingSanitizer {
    public static Double sanitize(double rating, double minimumValue, double maximumValue) {
        if (!Double.isFinite(rating)) return null;

        double effectiveMinimum = finiteOrDefault(minimumValue, -Double.MAX_VALUE);
        double effectiveMaximum = finiteOrDefault(maximumValue, Double.MAX_VALUE);
        if (effectiveMinimum > effectiveMaximum) {
            double temporaryValue = effectiveMinimum;
            effectiveMinimum = effectiveMaximum;
            effectiveMaximum = temporaryValue;
        }

        return Math.max(effectiveMinimum, Math.min(effectiveMaximum, rating));
    }

    private static double finiteOrDefault(double value, double fallback) {
        return Double.isFinite(value) ? value : fallback;
    }
}
