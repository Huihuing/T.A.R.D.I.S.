package com.tardistock.backend.util;

import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.ToLongFunction;

public final class BoundedCacheSupport {

    private BoundedCacheSupport() {}

    public static <K, V> void put(
            ConcurrentHashMap<K, V> cache,
            K key,
            V value,
            int maxEntries,
            ToLongFunction<V> expiresAt) {
        if (maxEntries <= 0) {
            throw new IllegalArgumentException(
                    "maxEntries must be greater than zero"
            );
        }

        synchronized (cache) {
            if (!cache.containsKey(key)
                    && cache.size() >= maxEntries) {
                long now = System.currentTimeMillis();
                cache.entrySet().removeIf(
                        entry -> expiresAt.applyAsLong(entry.getValue()) <= now
                );

                while (cache.size() >= maxEntries) {
                    K evictionKey = cache.entrySet().stream()
                            .min(Comparator.comparingLong(
                                    entry -> expiresAt.applyAsLong(
                                            entry.getValue()
                                    )
                            ))
                            .map(Map.Entry::getKey)
                            .orElse(null);
                    if (evictionKey == null) {
                        break;
                    }
                    cache.remove(evictionKey);
                }
            }

            cache.put(key, value);
        }
    }
}
