package com.redhat.migrationtoolkit.rhcl.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StringUtilsTest {

    @NullSource
    @ParameterizedTest
    void toKebabCase_null_returnsService(String input) {
        assertEquals("service", StringUtils.toKebabCase(input));
    }

    @ParameterizedTest
    @CsvSource({
            "my-api, my-api",
            "My API Service, my-api-service",
            "already-kebab, already-kebab",
            "UPPER, upper",
            "  spaced  , spaced",
            "dots.and_stuff, dots-and-stuff"
    })
    void toKebabCase_commonInputs(String input, String expected) {
        assertEquals(expected, StringUtils.toKebabCase(input));
    }

    @Test
    void toKebabCase_emptyAfterStrip_returnsEmpty() {
        assertEquals("", StringUtils.toKebabCase("---"));
    }
}
