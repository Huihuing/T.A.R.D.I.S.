package com.tardistock.backend.util;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * 동일 key에 대한 동시 작업을 하나로 합쳐 중복 외부 호출을 방지합니다.
 */
public final class SingleFlightSupport {

    private SingleFlightSupport() {}

    public static <K, V> V execute(
            ConcurrentHashMap<K, CompletableFuture<V>> inFlight,
            K key,
            Supplier<V> action) {
        CompletableFuture<V> candidate = new CompletableFuture<>();
        CompletableFuture<V> existing =
                inFlight.putIfAbsent(key, candidate);

        if (existing != null) {
            try {
                return existing.join();
            } catch (CompletionException e) {
                Throwable cause = e.getCause();
                if (cause instanceof RuntimeException runtimeException) {
                    throw runtimeException;
                }
                throw e;
            }
        }

        try {
            V result = action.get();
            candidate.complete(result);
            return result;
        } catch (RuntimeException e) {
            candidate.completeExceptionally(e);
            throw e;
        } finally {
            inFlight.remove(key, candidate);
        }
    }
}
