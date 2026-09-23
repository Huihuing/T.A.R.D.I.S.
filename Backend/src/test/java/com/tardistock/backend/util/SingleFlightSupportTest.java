package com.tardistock.backend.util;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SingleFlightSupportTest {

    @Test
    void coalescesConcurrentCallsForSameKey() throws Exception {
        ConcurrentHashMap<String, CompletableFuture<Integer>> inFlight =
                new ConcurrentHashMap<>();
        AtomicInteger executions = new AtomicInteger();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        ExecutorService executor = Executors.newFixedThreadPool(16);
        try {
            List<Future<Integer>> futures = new ArrayList<>();
            for (int i = 0; i < 32; i++) {
                futures.add(executor.submit(() ->
                        SingleFlightSupport.execute(
                                inFlight,
                                "AAPL",
                                () -> {
                                    executions.incrementAndGet();
                                    started.countDown();
                                    await(release);
                                    return 123;
                                }
                        )
                ));
            }

            started.await();
            release.countDown();

            for (Future<Integer> future : futures) {
                assertEquals(123, future.get());
            }
            assertEquals(1, executions.get());
            assertTrue(inFlight.isEmpty());
        } finally {
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
}
