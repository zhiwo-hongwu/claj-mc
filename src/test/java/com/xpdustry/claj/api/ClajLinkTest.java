package com.xpdustry.claj.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ClajLinkTest {

    @Test
    @DisplayName("Test ClajLink creation and roundtrip formatting")
    void testRoundtrip() {
        ClajLink link = new ClajLink("example.com", 50000, 12345678901234L);
        String str = link.toString();
        assertTrue(str.startsWith("claj://example.com:50000/"));

        ClajLink parsed = ClajLink.fromString(str);
        assertEquals(link.host, parsed.host);
        assertEquals(link.port, parsed.port);
        assertEquals(link.roomId, parsed.roomId);
        assertEquals(link.encodedRoomId, parsed.encodedRoomId);
        assertEquals(link, parsed);
        assertEquals(link.hashCode(), parsed.hashCode());
    }

    @Test
    @DisplayName("Test invalid ClajLink parsing")
    void testInvalidLink() {
        assertThrows(IllegalArgumentException.class, () -> ClajLink.fromString(""));
        assertThrows(IllegalArgumentException.class, () -> ClajLink.fromString("http://example.com:50000/room"));
        assertThrows(IllegalArgumentException.class, () -> ClajLink.fromString("claj://example.com/no-port"));
        assertThrows(IllegalArgumentException.class, () -> ClajLink.fromString("claj://example.com:invalid/room"));
    }
}
