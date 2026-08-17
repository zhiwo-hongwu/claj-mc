package com.xpdustry.claj.common.util;

import java.net.InetAddress;
import java.net.UnknownHostException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AddressUtilTest {

    @Test
    @DisplayName("Test AddressUtil hash and IPv6 generation")
    void testHashAndGenerate() throws UnknownHostException {
        InetAddress ipv4 = InetAddress.getByName("192.168.1.100");
        long hash = AddressUtil.hash(ipv4);
        assertNotEquals(0L, hash);

        InetAddress generated = AddressUtil.generate(hash);
        assertNotNull(generated);
        assertEquals(16, generated.getAddress().length);
        assertEquals((byte) 0xfd, generated.getAddress()[0]);

        InetAddress obfuscated = AddressUtil.obfuscate(ipv4);
        assertNotNull(obfuscated);
        assertEquals(generated, obfuscated);
    }

    @Test
    @DisplayName("Test AddressUtil null safety")
    void testNullSafety() {
        assertEquals(0L, AddressUtil.hash((InetAddress) null));
        assertEquals(0L, AddressUtil.hash((arc.net.Connection) null));
        assertNull(AddressUtil.get(null));
        assertNull(AddressUtil.getString(null));
    }

    @Test
    @DisplayName("Test encodeId")
    void testEncodeId() {
        String idStr = AddressUtil.encodeId(0x12AB);
        assertNotNull(idStr);
        assertTrue(idStr.startsWith("0x"));
    }
}
