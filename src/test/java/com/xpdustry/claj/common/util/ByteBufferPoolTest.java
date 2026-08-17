package com.xpdustry.claj.common.util;

import java.nio.ByteBuffer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ByteBufferPoolTest {

    @Test
    @DisplayName("Test ByteBufferPool obtain and release")
    void testObtainAndRelease() {
        ByteBufferPool pool = new ByteBufferPool(1024, 16);
        ByteBuffer buf = pool.obtain(500, false);
        assertNotNull(buf);
        assertEquals(500, buf.remaining());
        assertEquals(1024, buf.capacity());

        boolean freed = pool.release(buf);
        assertTrue(freed);
        assertEquals(1, pool.size(1, false));

        ByteBuffer reused = pool.obtain(800, false);
        assertNotNull(reused);
        assertEquals(800, reused.remaining());
        assertEquals(0, pool.size(1, false));
    }

    @Test
    @DisplayName("Test zero and negative size obtain")
    void testZeroSize() {
        ByteBufferPool pool = ByteBufferPool.get();
        ByteBuffer empty = pool.obtain(0);
        assertEquals(0, empty.capacity());
    }
}
