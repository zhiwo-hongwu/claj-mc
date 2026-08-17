package com.xpdustry.claj.common.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ClajTypeTest {

    @Test
    @DisplayName("Test ClajType creation and validation")
    void testCreation() {
        ClajType mc = ClajType.of("Minecraft");
        assertNotNull(mc);
        assertEquals("Minecraft", mc.type());
        assertEquals("Minecraft", mc.toString());

        ClajType mc2 = ClajType.of("Minecraft");
        assertEquals(mc, mc2);
        assertEquals(mc.hashCode(), mc2.hashCode());

        ClajType mindustry = ClajType.of("Mindustry");
        assertNotEquals(mc, mindustry);
    }

    @Test
    @DisplayName("Test ClajType bounds and invalid inputs")
    void testInvalidType() {
        assertNull(ClajType.of(""));
        assertNull(ClajType.of("   "));
        assertNull(ClajType.of(null));
        // Test type length limit (> 32 chars)
        String tooLong = "A".repeat(33);
        assertNull(ClajType.of(tooLong));
    }
}
