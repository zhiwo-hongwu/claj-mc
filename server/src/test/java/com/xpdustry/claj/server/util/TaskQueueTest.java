package com.xpdustry.claj.server.util;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TaskQueueTest {

    @Test
    @DisplayName("Test TaskQueue single threaded execution")
    void testSingleThreaded() {
        TaskQueue queue = new TaskQueue();
        AtomicInteger counter = new AtomicInteger();

        queue.post(counter::incrementAndGet);
        queue.post(counter::incrementAndGet);
        assertEquals(2, queue.size());

        queue.run();
        assertEquals(2, counter.get());
        assertEquals(0, queue.size());
    }

    @Test
    @DisplayName("Test TaskQueue clear")
    void testClear() {
        TaskQueue queue = new TaskQueue();
        queue.post(() -> {});
        queue.post(() -> {});
        assertEquals(2, queue.size());

        queue.clear();
        assertEquals(0, queue.size());
    }
}
