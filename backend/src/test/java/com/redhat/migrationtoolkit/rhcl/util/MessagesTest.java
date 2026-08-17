package com.redhat.migrationtoolkit.rhcl.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

class MessagesTest {

    private Messages messages;

    @BeforeEach
    void setUp() {
        messages = new Messages();
    }

    @Test
    void get_existingKey_returnsMessage() {
        String msg = messages.get("apply.success");
        assertEquals("Applied successfully", msg);
    }

    @Test
    void get_applyErrorNoFiles_returnsEnglishMessageByDefault() {
        String msg = messages.get("apply.error.noFiles");
        assertEquals("No files to apply", msg);
    }

    @Test
    void get_applyErrorNoFiles_returnsJapaneseMessage() {
        String msg = messages.get("apply.error.noFiles", Locale.JAPANESE);
        assertEquals("適用するファイルがありません", msg);
    }

    @Test
    void get_importErrorNoFile_returnsMessage() {
        String msg = messages.get("import.error.noFile");
        assertEquals("No file was uploaded", msg);
    }

    @Test
    void get_importErrorNoYaml_returnsMessage() {
        String msg = messages.get("import.error.noYaml");
        assertEquals("No YAML files found in the ZIP archive", msg);
    }

    @Test
    void get_nonExistentKey_returnsKeyItself() {
        String key = "does.not.exist.abc123";
        String msg = messages.get(key);
        assertEquals(key, msg);
    }

    @Test
    void get_withFormatArgs_replacesPlaceholder() {
        String msg = messages.get("import.error.parseZip", "connection refused");
        assertEquals("Failed to parse ZIP file: connection refused", msg);
    }

    @Test
    void get_withMultipleArgs_formatsCorrectly() {
        String msg = messages.get("import.error.parseZip", "timeout", "extra");
        assertEquals("Failed to parse ZIP file: timeout", msg);
    }

    @Test
    void get_nullArgs_doesNotThrow() {
        assertDoesNotThrow(() -> messages.get("apply.success"));
    }

    @Test
    void get_emptyArgs_returnsPattern() {
        String msg = messages.get("apply.success");
        assertEquals("Applied successfully", msg);
    }
}
