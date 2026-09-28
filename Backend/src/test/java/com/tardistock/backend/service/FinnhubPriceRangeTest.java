package com.tardistock.backend.service;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FinnhubPriceRangeTest {

    @Test
    void rejectsNonFiniteProviderPriceWithoutCachingIt() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        when(restTemplate.getForObject(anyString(), eq(Map.class)))
                .thenReturn(Map.of("c", "Infinity"));

        FinnhubPriceService service = service(restTemplate);

        assertEquals(0.0, service.getPrice("AAPL"));
        assertEquals(0, service.cacheSizeForTest());
        assertEquals(0.0, service.getPrice("AAPL"));

        verify(restTemplate, times(2))
                .getForObject(anyString(), eq(Map.class));
    }

    @Test
    void rejectsProviderPriceOutsideDecimal19Scale6WithoutCachingIt() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        when(restTemplate.getForObject(anyString(), eq(Map.class)))
                .thenReturn(Map.of("c", 10_000_000_000_000d));

        FinnhubPriceService service = service(restTemplate);

        assertEquals(0.0, service.getPrice("MSFT"));
        assertEquals(0, service.cacheSizeForTest());
        assertEquals(0.0, service.getPrice("MSFT"));

        verify(restTemplate, times(2))
                .getForObject(anyString(), eq(Map.class));
    }

    private FinnhubPriceService service(RestTemplate restTemplate) {
        FinnhubPriceService service = new FinnhubPriceService(restTemplate);
        ReflectionTestUtils.setField(service, "apiKey", "test-key");
        return service;
    }
}
