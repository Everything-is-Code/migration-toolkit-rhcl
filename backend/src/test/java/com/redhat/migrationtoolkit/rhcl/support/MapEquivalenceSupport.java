package com.redhat.migrationtoolkit.rhcl.support;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/** Deep structural comparison of parsed YAML maps (order-insensitive at each level). */
public final class MapEquivalenceSupport {

    private MapEquivalenceSupport() {
    }

    @SuppressWarnings("unchecked")
    public static void assertEquivalent(Map<String, Object> expected, Map<String, Object> actual) {
        if (expected == null || actual == null) {
            assertEquals(expected, actual);
            return;
        }
        assertEquals(expected.keySet(), actual.keySet(), "top-level keys differ");
        for (String key : expected.keySet()) {
            assertEquivalentValue(key, expected.get(key), actual.get(key));
        }
    }

    @SuppressWarnings("unchecked")
    private static void assertEquivalentValue(String path, Object expected, Object actual) {
        if (expected instanceof Map<?, ?> expectedMap && actual instanceof Map<?, ?> actualMap) {
            Map<String, Object> expectedStringMap = (Map<String, Object>) expectedMap;
            Map<String, Object> actualStringMap = (Map<String, Object>) actualMap;
            assertEquals(expectedStringMap.keySet(), actualStringMap.keySet(), path + " keys differ");
            for (String key : expectedStringMap.keySet()) {
                assertEquivalentValue(path + "." + key, expectedStringMap.get(key), actualStringMap.get(key));
            }
            return;
        }
        if (expected instanceof List<?> expectedList && actual instanceof List<?> actualList) {
            assertEquals(expectedList.size(), actualList.size(), path + " list size");
            for (int i = 0; i < expectedList.size(); i++) {
                assertEquivalentValue(path + "[" + i + "]", expectedList.get(i), actualList.get(i));
            }
            return;
        }
        if (!Objects.equals(normalizeNumber(expected), normalizeNumber(actual))) {
            fail(path + " expected <" + expected + "> but was <" + actual + ">");
        }
    }

    private static Object normalizeNumber(Object value) {
        if (value instanceof Integer i) {
            return i.longValue();
        }
        return value;
    }
}
