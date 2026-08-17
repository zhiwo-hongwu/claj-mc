package com.xpdustry.claj.common.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StringsTest {

    @Test
    @DisplayName("Test longToBase64 and base64ToLong")
    void testBase64Conversion() {
        long[] values = {0L, 1L, -1L, 123456789L, Long.MAX_VALUE, Long.MIN_VALUE};
        for (long v : values) {
            String b64 = Strings.longToBase64(v);
            assertNotNull(b64);
            long parsed = Strings.base64ToLong(b64);
            assertEquals(v, parsed);
        }
    }

    @Test
    @DisplayName("Test string manipulation utilities")
    void testUtilities() {
        assertEquals("hello-world", Strings.camelToKebab("helloWorld"));
        assertEquals("Hello", Strings.capitalize("hello"));
        assertTrue(Strings.isTrue("true"));
        assertTrue(Strings.isTrue("1"));
        assertTrue(Strings.isFalse("false"));
        assertTrue(Strings.isFalse("0"));
    }

    @Test
    @DisplayName("Test version comparison")
    void testVersionCompare() {
        assertTrue(Strings.compareVersion("1.2.3", "1.2.0") > 0);
        assertEquals(0, Strings.compareVersion("2.0.0", "2.0.0"));
        assertTrue(Strings.compareVersion("1.0.0", "1.0.1") < 0);
    }
}
