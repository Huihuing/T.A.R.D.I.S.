package com.tardistock.backend.controller;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ApiExceptionHandlerTest {

    private final ApiExceptionHandler handler =
            new ApiExceptionHandler();

    @Test
    void illegalArgumentReturnsBadRequest() {
        ResponseEntity<?> response =
                handler.handleIllegalArgument(
                        new IllegalArgumentException("잘못된 값")
                );

        assertEquals(400, response.getStatusCode().value());
        assertEquals(
                "잘못된 값",
                ((Map<?, ?>) response.getBody()).get("message")
        );
    }

    @Test
    void illegalStateReturnsConflict() {
        ResponseEntity<?> response =
                handler.handleIllegalState(
                        new IllegalStateException("이미 처리됨")
                );

        assertEquals(409, response.getStatusCode().value());
    }

    @Test
    void oversizedUploadReturnsPayloadTooLarge() {
        ResponseEntity<?> response =
                handler.handleUploadTooLarge(
                        new MaxUploadSizeExceededException(
                                6L * 1024L * 1024L
                        )
                );

        assertEquals(413, response.getStatusCode().value());
    }

    @Test
    void unexpectedExceptionHidesInternalMessage() {
        ResponseEntity<?> response =
                handler.handleUnexpected(
                        new RuntimeException("sensitive-internal-detail")
                );

        assertEquals(500, response.getStatusCode().value());
        assertEquals(
                "서버에서 요청을 처리하는 중 오류가 발생했습니다.",
                ((Map<?, ?>) response.getBody()).get("message")
        );
    }
}
