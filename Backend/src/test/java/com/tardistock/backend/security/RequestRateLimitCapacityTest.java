package com.tardistock.backend.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RequestRateLimitCapacityTest {

    @Test
    void fullCapacityStillAllowsExistingCounterButRejectsNewKey() throws Exception {
        RequestRateLimitFilter filter = new RequestRateLimitFilter(2);

        assertEquals(200, invokeLogin(filter, "203.0.113.1"));
        assertEquals(200, invokeLogin(filter, "203.0.113.2"));

        assertEquals(200, invokeLogin(filter, "203.0.113.1"));
        assertEquals(429, invokeLogin(filter, "203.0.113.3"));
    }

    @Test
    void concurrentNewKeysCannotRacePastCounterCapacity() throws Exception {
        RequestRateLimitFilter filter = new RequestRateLimitFilter(8);

        for (int i = 1; i <= 7; i++) {
            assertEquals(200, invokeLogin(filter, "203.0.113." + i));
        }

        int contenders = 32;
        ExecutorService executor = Executors.newFixedThreadPool(contenders);
        CountDownLatch ready = new CountDownLatch(contenders);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();

        try {
            for (int i = 1; i <= contenders; i++) {
                String clientIp = "198.51.100." + i;
                results.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return invokeLogin(filter, clientIp);
                }));
            }

            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();

            int accepted = 0;
            int limited = 0;
            for (Future<Integer> result : results) {
                int status = result.get(10, TimeUnit.SECONDS);
                if (status == 200) {
                    accepted++;
                } else if (status == 429) {
                    limited++;
                }
            }

            assertEquals(1, accepted);
            assertEquals(contenders - 1, limited);
        } finally {
            start.countDown();
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    private int invokeLogin(
            RequestRateLimitFilter filter,
            String remoteAddress) throws Exception {
        MockHttpServletRequest request =
                new MockHttpServletRequest("POST", "/api/auth/login");
        request.setRemoteAddr(remoteAddress);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> {});
        return response.getStatus();
    }
}
