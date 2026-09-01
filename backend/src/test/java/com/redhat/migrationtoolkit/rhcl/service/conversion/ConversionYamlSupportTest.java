package com.redhat.migrationtoolkit.rhcl.service.conversion;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConversionYamlSupportTest {

    private ConversionYamlSupport support;

    @BeforeEach
    void setUp() {
        support = new ConversionYamlSupport();
    }

    @Test
    void stripMigratedFromLabel_removesLabelLine() {
        String input = """
                metadata:
                  labels:
                    app: demo
                    migrated-from: 3scale
                """;

        String stripped = support.stripMigratedFromLabel(input);

        assertTrue(stripped.contains("app: demo"));
        assertTrue(stripped.contains("labels:"));
        assertTrue(!stripped.contains("migrated-from: 3scale"));
    }

    @Test
    void stripMigratedFromLabel_removesQuotedLabelLine() {
        String input = """
                metadata:
                  labels:
                    app: demo
                    migrated-from: "3scale"
                """;

        String stripped = support.stripMigratedFromLabel(input);

        assertTrue(stripped.contains("app: demo"));
        assertTrue(!stripped.contains("migrated-from"));
    }

    @Test
    void stripMigratedFromLabel_removesFlowStyleLabel() {
        String input = """
                metadata:
                  labels: {app: demo, migrated-from: 3scale}
                """;

        String stripped = support.stripMigratedFromLabel(input);

        assertTrue(stripped.contains("app: demo"));
        assertTrue(!stripped.contains("migrated-from"));
    }

    @Test
    void stripMigratedFromLabel_removesSoleFlowStyleLabel() {
        String input = """
                metadata:
                  labels: {migrated-from: 3scale}
                """;

        String stripped = support.stripMigratedFromLabel(input);

        assertTrue(stripped.contains("labels: {}"));
        assertTrue(!stripped.contains("migrated-from"));
    }

    @Test
    void normalizeLineEndings_convertsCrlfToLf() {
        String crlf = "a\r\nb\rc";
        assertEquals("a\nb\nc", ConversionYamlSupport.normalizeLineEndings(crlf));
        assertEquals("plain", ConversionYamlSupport.normalizeLineEndings("plain"));
    }

    @Test
    void parseJsonObjectConfig_listAndString() {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("name", "demo");
        List<Map<String, Object>> fromList = support.parseJsonObjectConfig(List.of(item));
        List<Map<String, Object>> fromString = support.parseJsonObjectConfig("[{\"name\":\"demo\"}]");

        assertEquals(1, fromList.size());
        assertEquals("demo", fromList.get(0).get("name"));
        assertEquals(1, fromString.size());
        assertEquals("demo", fromString.get(0).get("name"));
        assertTrue(support.parseJsonObjectConfig("not-json").isEmpty());
    }

    @Test
    void toEnvoyVar_replacesNginxVariables() {
        String converted = ConversionYamlSupport.toEnvoyVar(
                "$request_method $request_uri $status $remote_addr");

        assertTrue(converted.contains("%REQ(:METHOD)%"));
        assertTrue(converted.contains("%RESPONSE_CODE%"));
        assertTrue(converted.contains("%DOWNSTREAM_REMOTE_ADDRESS_WITHOUT_PORT%"));
    }

    @Test
    void pcreToLuaPattern_convertsCommonEscapes() {
        assertEquals("%d+%w", ConversionYamlSupport.pcreToLuaPattern("\\d+\\w"));
    }

    @Test
    void pcreReplaceToLua_convertsCaptureGroups() {
        assertEquals("/api/%1", ConversionYamlSupport.pcreReplaceToLua("/api/$1"));
        assertEquals("/api/%2", ConversionYamlSupport.pcreReplaceToLua("/api/\\2"));
    }
}
