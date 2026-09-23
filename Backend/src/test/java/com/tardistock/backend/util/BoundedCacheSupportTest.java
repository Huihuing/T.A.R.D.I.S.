package com.tardistock.backend.util;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BoundedCacheSupportTest {

    @Test
    void removesExpiredEntriesBeforeEvictingFreshOnes() {
        long now = System.currentTimeMillis();
        ConcurrentHashMap<String, Entry> cache = new ConcurrentHashMap<>();
        cache.put("expired-a", new Entry("a", now - 2_000));
        cache.put("expired-b", new Entry("b", now - 1_000));
        cache.put("fresh", new Entry("fresh", now + 10_000));

        BoundedCacheSupport.put(
                cache,
                "new",
                new Entry("new", now + 20_000),
                3,
                Entry::expiresAt
        );

        assertEquals(2, cache.size());
        assertFalse(cache.containsKey("expired-a"));
        assertFalse(cache.containsKey("expired-b"));
        assertTrue(cache.containsKey("fresh"));
        assertTrue(cache.containsKey("new"));
    }

    @Test
    void evictsOnlyOldestFreshEntryWhenFull() {
        long now = System.currentTimeMillis();
        ConcurrentHashMap<String, Entry> cache = new ConcurrentHashMap<>();
        cache.put("oldest", new Entry("oldest", now + 1_000));
        cache.put("middle", new Entry("middle", now + 2_000));
        cache.put("newest", new Entry("newest", now + 3_000));

        BoundedCacheSupport.put(
                cache,
                "incoming",
                new Entry("incoming", now + 4_000),
                3,
                Entry::expiresAt
        );

        assertEquals(3, cache.size());
        assertFalse(cache.containsKey("oldest"));
        assertTrue(cache.containsKey("middle"));
        assertTrue(cache.containsKey("newest"));
        assertTrue(cache.containsKey("incoming"));
    }

    @Test
    void shrinksPreexistingOversizedCacheBackToMaximum() {
        long now = System.currentTimeMillis();
        ConcurrentHashMap<String, Entry> cache = new ConcurrentHashMap<>();
        cache.put("a", new Entry("a", now + 1_000));
        cache.put("b", new Entry("b", now + 2_000));
        cache.put("c", new Entry("c", now + 3_000));
        cache.put("d", new Entry("d", now + 4_000));
        cache.put("e", new Entry("e", now + 5_000));

        BoundedCacheSupport.put(
                cache,
                "incoming",
                new Entry("incoming", now + 6_000),
                3,
                Entry::expiresAt
        );

        assertEquals(3, cache.size());
        assertFalse(cache.containsKey("a"));
        assertFalse(cache.containsKey("b"));
        assertFalse(cache.containsKey("c"));
        assertTrue(cache.containsKey("d"));
        assertTrue(cache.containsKey("e"));
        assertTrue(cache.containsKey("incoming"));
    }

    @Test
    void remainsStrictlyBoundedUnderConcurrentWrites() throws Exception {
        int maxEntries = 100;
        long expiresAt = System.currentTimeMillis() + 60_000;
        ConcurrentHashMap<String, Entry> cache = new ConcurrentHashMap<>();
        ExecutorService executor = Executors.newFixedThreadPool(32);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();

        try {
            for (int i = 0; i < 300; i++) {
                int index = i;
                futures.add(executor.submit(() -> {
                    assertTrue(start.await(5, TimeUnit.SECONDS));
                    BoundedCacheSupport.put(
                            cache,
                            "key-" + index,
                            new Entry("value-" + index, expiresAt + index),
                            maxEntries,
                            Entry::expiresAt
                    );
                    return null;
                }));
            }

            start.countDown();
            for (Future<?> future : futures) {
                future.get(10, TimeUnit.SECONDS);
            }

            assertEquals(maxEntries, cache.size());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void rejectsNonPositiveMaximum() {
        ConcurrentHashMap<String, Entry> cache = new ConcurrentHashMap<>();

        assertThrows(
                IllegalArgumentException.class,
                () -> BoundedCacheSupport.put(
                        cache,
                        "key",
                        new Entry("value", System.currentTimeMillis()),
                        0,
                        Entry::expiresAt
                )
        );
    }

    private record Entry(String value, long expiresAt) {}
}
