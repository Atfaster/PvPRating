package dev.pvprating.utils;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AuditJsonTest {
    @Test
    void escapesStringCharactersThatCouldBreakJsonLines() {
        String json = AuditJson.toJson("name\"\\\n\r\t\u0001");

        assertEquals("\"name\\\"\\\\\\n\\r\\t\\u0001\"", json);
    }

    @Test
    void serializesNestedObjectsAndArrays() {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("event", "rating");
        fields.put("values", List.of(1, true, "ok"));

        String json = AuditJson.toJson(fields);

        assertEquals("{\"event\":\"rating\",\"values\":[1,true,\"ok\"]}", json);
    }

    @Test
    void writesNonFiniteFloatingPointValuesAsStrings() {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("nan", Double.NaN);
        fields.put("positiveInfinity", Double.POSITIVE_INFINITY);

        String json = AuditJson.toJson(fields);

        assertEquals("{\"nan\":\"NaN\",\"positiveInfinity\":\"Infinity\"}", json);
    }
}
