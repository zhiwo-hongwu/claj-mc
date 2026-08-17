package com.xpdustry.claj.server.util;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import arc.files.Fi;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JsonSettingsTest {

    private File tempFile;
    private JsonSettings settings;

    @BeforeEach
    void setUp() throws IOException {
        tempFile = File.createTempFile("test_settings", ".json");
        settings = new JsonSettings(new Fi(tempFile), false);
    }

    @AfterEach
    void tearDown() {
        if (tempFile != null && tempFile.exists()) {
            tempFile.delete();
        }
    }

    @Test
    @DisplayName("Test getLong and default fallback without ClassCastException")
    void testGetLongDefault() {
        // When key does not exist, getLong should return 0L without ClassCastException
        long defaultVal = settings.getLong("non_existent_key");
        assertEquals(0L, defaultVal);

        settings.put("my_long", 12345678901234L);
        assertEquals(12345678901234L, settings.getLong("my_long"));
    }

    @Test
    @DisplayName("Test basic primitives put and get")
    void testPrimitives() {
        settings.put("string_val", "hello");
        settings.put("int_val", 42);
        settings.put("bool_val", true);

        assertEquals("hello", settings.getString("string_val"));
        assertEquals(42, settings.getInt("int_val"));
        assertTrue(settings.getBool("bool_val"));
    }
}
