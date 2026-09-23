package com.tardistock.backend.util;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SingleFlightSupportTest {

    @Test
    void coalescesConcurrentCallsForSameKey() throws Exception {
        TrackingMap<String, CompletableFuture<Integer>> inFlight =
                new TrackingMap<>();
        AtomicInteger leaderExecutions = new AtomicInteger();
        AtomicInteger followerExecutions = new AtomicInteger();
        CountDownLatch leaderStarted = new CountDownLatch(1);
        CountDownLatch releaseLeader = new CountDownLatch(1);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> leader = executor.submit(() ->
                    SingleFlightSupport.execute(
                            inFlight,
                            "AAPL",
                            () -> {
                                leaderExecutions.incrementAndGet();
                                leaderStarted.countDown();
                                await(releaseLeader);
                                return 123;
                            }
                    )
            );

            leaderStarted.await();

            Future<Integer> follower = executor.submit(() ->
                    SingleFlightSupport.execute(
                            inFlight,
                            "AAPL",
                            () -> {
                                followerExecutions.incrementAndGet();
                                return 999;
                            }
                    )
            );

            inFlight.awaitSecondPutIfAbsent();
            assertFalse(follower.isDone());

            releaseLeader.countDown();

            assertEquals(123, leader.get());
            assertEquals(123, follower.get());
            assertEquals(1, leaderExecutions.get());
            assertEquals(0, followerExecutions.get());
            assertTrue(inFlight.isEmpty());
        } finally {
            releaseLeader.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void differentKeysCanExecuteIndependently() {
        ConcurrentHashMap<String, CompletableFuture<Integer>> inFlight =
                new ConcurrentHashMap<>();
        AtomicInteger executions = new AtomicInteger();

        int first = SingleFlightSupport.execute(
                inFlight,
                "AAPL",
                executions::incrementAndGet
        );
        int second = SingleFlightSupport.execute(
                inFlight,
                "MSFT",
                executions::incrementAndGet
        );

        assertEquals(1, first);
        assertEquals(2, second);
        assertEquals(2, executions.get());
        assertTrue(inFlight.isEmpty());
    }

    @Test
    void clearsFailedFlightSoNextCallCanRetry() {
        ConcurrentHashMap<String, CompletableFuture<Integer>> inFlight =
                new ConcurrentHashMap<>();
        AtomicInteger executions = new AtomicInteger();

        assertThrows(
                IllegalStateException.class,
                () -> SingleFlightSupport.execute(
                        inFlight,
                        "AAPL",
                        () -> {
                            executions.incrementAndGet();
                            throw new IllegalStateException("boom");
                        }
                )
        );

        int result = SingleFlightSupport.execute(
                inFlight,
                "AAPL",
                () -> {
                    executions.incrementAndGet();
                    return 42;
                }
        );

        assertEquals(42, result);
        assertEquals(2, executions.get());
        assertTrue(inFlight.isEmpty());
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted", e);
        }
    }

    private static final class TrackingMap<K, V>
            extends ConcurrentHashMap<K, V> {
        private final AtomicInteger putIfAbsentCalls = new AtomicInteger();
        private final CountDownLatch secondPutIfAbsent = new CountDownLatch(1);

        @Override
        public V putIfAbsent(K key, V value) {
            int call = putIfAbsentCalls.incrementAndGet();
            V existing = super.putIfAbsent(key, value);
            if (call == 2) {
                secondPutIfAbsent.countDown();
            }
            return existing;
        }

        void awaitSecondPutIfAbsent() throws InterruptedException {
            secondPutIfAbsent.await();
        }
    }
}
