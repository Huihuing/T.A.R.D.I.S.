package com.tardistock.backend.service;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FinnhubPriceServiceTest {

    @Test
    void reusesFreshPriceCache() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        when(restTemplate.getForObject(anyString(), eq(Map.class)))
                .thenReturn(Map.of("c", 123.45));

        FinnhubPriceService service = service(restTemplate);

        assertEquals(123.45, service.getPrice("AAPL"));
        assertEquals(123.45, service.getPrice("aapl"));

        verify(restTemplate, times(1))
                .getForObject(anyString(), eq(Map.class));
    }

    @Test
    void concurrentCacheMissesShareOneFinnhubCall() throws Exception {
        RestTemplate restTemplate = mock(RestTemplate.class);
        CountDownLatch callStarted = new CountDownLatch(1);
        CountDownLatch allowResponse = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();

        when(restTemplate.getForObject(anyString(), eq(Map.class)))
                .thenAnswer(invocation -> {
                    calls.incrementAndGet();
                    callStarted.countDown();
                    assertTrue(allowResponse.await(5, TimeUnit.SECONDS));
                    return Map.of("c", 222.22);
                });

        FinnhubPriceService service = service(restTemplate);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Double> first =
                    executor.submit(() -> service.getPrice("MSFT"));

            assertTrue(callStarted.await(5, TimeUnit.SECONDS));

            Future<Double> second =
                    executor.submit(() -> service.getPrice("msft"));

            allowResponse.countDown();

            assertEquals(222.22, first.get(5, TimeUnit.SECONDS));
            assertEquals(222.22, second.get(5, TimeUnit.SECONDS));
            assertEquals(1, calls.get());
            verify(restTemplate, times(1))
                    .getForObject(anyString(), eq(Map.class));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void priceCacheRemainsBounded() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        when(restTemplate.getForObject(anyString(), eq(Map.class)))
                .thenReturn(Map.of("c", 10.0));

        FinnhubPriceService service = service(restTemplate);

        for (int i = 0; i < 501; i++) {
            service.getPrice("S" + i);
        }

        assertEquals(500, service.cacheSizeForTest());
        verify(restTemplate, times(501))
                .getForObject(anyString(), eq(Map.class));
    }

    @Test
    void invalidSymbolDoesNotCallProvider() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        FinnhubPriceService service = service(restTemplate);

        assertEquals(0.0, service.getPrice("../../etc/passwd"));

        verify(restTemplate, times(0))
                .getForObject(anyString(), eq(Map.class));
    }

    private FinnhubPriceService service(RestTemplate restTemplate) {
        FinnhubPriceService service =
                new FinnhubPriceService(restTemplate);
        ReflectionTestUtils.setField(service, "apiKey", "test-key");
        return service;
    }
}
