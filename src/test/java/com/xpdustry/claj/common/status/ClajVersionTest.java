package com.xpdustry.claj.common.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ClajVersionTest {

    @Test
    @DisplayName("Test parsing valid version strings")
    void testParseValid() {
        ClajVersion v1 = ClajVersion.of("2.4.2");
        assertEquals(2, v1.protocolVersion);
        assertEquals(4, v1.majorVersion);
        assertEquals(2, v1.minorVersion);
        assertEquals("2.4.2", v1.toString());

        ClajVersion v2 = ClajVersion.of("2.4");
        assertEquals(2, v2.protocolVersion);
        assertEquals(4, v2.majorVersion);
        assertEquals(0, v2.minorVersion);
    }

    @Test
    @DisplayName("Test version comparisons")
    void testComparison() {
        ClajVersion v1 = ClajVersion.of(2, 4, 1);
        ClajVersion v2 = ClajVersion.of(2, 4, 2);
        ClajVersion v3 = ClajVersion.of(2, 5, 0);

        assertTrue(v2.isAtLeast(v1));
        assertFalse(v1.isAtLeast(v2));
        assertTrue(v3.isAtLeast(v2));
        assertEquals(0, v2.compareTo(ClajVersion.of("2.4.2")));
        assertEquals(v1.hashCode(), ClajVersion.of(2, 4, 1).hashCode());
        assertEquals(v1, ClajVersion.of(2, 4, 1));
        assertNotEquals(v1, v2);
    }

    @Test
    @DisplayName("Test invalid version string handling")
    void testInvalidVersions() {
        assertThrows(IllegalArgumentException.class, () -> ClajVersion.of("invalid"));
        assertThrows(IllegalArgumentException.class, () -> ClajVersion.of("2"));
        assertThrows(IllegalArgumentException.class, () -> ClajVersion.of("2.4.2.1"));
        assertThrows(IllegalArgumentException.class, () -> ClajVersion.of(-1, 0, 0));
    }
}
