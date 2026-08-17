package com.xpdustry.claj.common.util;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PoolTest {

    static class TestObject {
        int id;
        TestObject(int id) { this.id = id; }
    }

    @Test
    @DisplayName("Test Pool obtain and free")
    void testObtainAndFree() {
        AtomicInteger created = new AtomicInteger();
        Pool<TestObject> pool = new Pool<>(16, () -> new TestObject(created.incrementAndGet()));

        assertEquals(0, pool.getFree());
        TestObject obj1 = pool.obtain();
        assertEquals(1, obj1.id);
        assertEquals(1, created.get());

        pool.free(obj1);
        assertEquals(1, pool.getFree());

        TestObject obj2 = pool.obtain();
        assertSame(obj1, obj2);
        assertEquals(0, pool.getFree());
        assertEquals(1, created.get());
    }
}
