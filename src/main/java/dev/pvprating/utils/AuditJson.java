package dev.pvprating.utils;

import java.util.Map;

public class AuditJson {
    public static String toJson(Object value) {
        StringBuilder builder = new StringBuilder();
        appendJsonValue(builder, value);
        return builder.toString();
    }

    @SuppressWarnings("unchecked")
    private static void appendJsonValue(StringBuilder builder, Object value) {
        if (value == null) {
            builder.append("null");
        } else if (value instanceof String stringValue) {
            appendJsonString(builder, stringValue);
        } else if (value instanceof Boolean booleanValue) {
            builder.append(booleanValue);
        } else if (value instanceof Float floatValue) {
            appendFloatingPointValue(builder, floatValue);
        } else if (value instanceof Double doubleValue) {
            appendFloatingPointValue(builder, doubleValue);
        } else if (value instanceof Number numberValue) {
            builder.append(numberValue);
        } else if (value instanceof Map<?, ?> mapValue) {
            appendJsonObject(builder, (Map<Object, Object>) mapValue);
        } else if (value instanceof Iterable<?> iterableValue) {
            appendJsonArray(builder, iterableValue);
        } else {
            appendJsonString(builder, String.valueOf(value));
        }
    }

    private static void appendFloatingPointValue(StringBuilder builder, Number value) {
        double doubleValue = value.doubleValue();
        if (Double.isFinite(doubleValue)) {
            builder.append(value);
        } else {
            appendJsonString(builder, String.valueOf(value));
        }
    }

    private static void appendJsonObject(StringBuilder builder, Map<Object, Object> map) {
        builder.append('{');
        boolean first = true;

        for (Map.Entry<Object, Object> entry : map.entrySet()) {
            if (!first) builder.append(',');
            first = false;

            appendJsonString(builder, String.valueOf(entry.getKey()));
            builder.append(':');
            appendJsonValue(builder, entry.getValue());
        }

        builder.append('}');
    }

    private static void appendJsonArray(StringBuilder builder, Iterable<?> values) {
        builder.append('[');
        boolean first = true;

        for (Object value : values) {
            if (!first) builder.append(',');
            first = false;

            appendJsonValue(builder, value);
        }

        builder.append(']');
    }

    private static void appendJsonString(StringBuilder builder, String value) {
        builder.append('"');

        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '"' -> builder.append("\\\"");
                case '\\' -> builder.append("\\\\");
                case '\b' -> builder.append("\\b");
                case '\f' -> builder.append("\\f");
                case '\n' -> builder.append("\\n");
                case '\r' -> builder.append("\\r");
                case '\t' -> builder.append("\\t");
                default -> {
                    if (character < 0x20) {
                        builder.append(String.format("\\u%04x", (int) character));
                    } else {
                        builder.append(character);
                    }
                }
            }
        }

        builder.append('"');
    }
}
