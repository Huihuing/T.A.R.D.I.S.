package com.tardistock.backend.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log =
            LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<?> handleIllegalArgument(
            IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of(
                "message",
                safeMessage(e.getMessage(), "요청 값이 올바르지 않습니다.")
        ));
    }

    @ExceptionHandler({
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class
    })
    public ResponseEntity<?> handleRequestBinding(Exception e) {
        return ResponseEntity.badRequest().body(Map.of(
                "message",
                "요청 파라미터가 올바르지 않습니다."
        ));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<?> handleUnreadableBody(
            HttpMessageNotReadableException e) {
        return ResponseEntity.badRequest().body(Map.of(
                "message",
                "요청 본문 형식이 올바르지 않습니다."
        ));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<?> handleUploadTooLarge(
            MaxUploadSizeExceededException e) {
        return ResponseEntity.status(413).body(Map.of(
                "message",
                "업로드 파일은 5MB 이하만 허용됩니다."
        ));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<?> handleNotFound(
            NoResourceFoundException e) {
        return ResponseEntity.status(404).body(Map.of(
                "message",
                "요청한 경로를 찾을 수 없습니다."
        ));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<?> handleMethodNotAllowed(
            HttpRequestMethodNotSupportedException e) {
        return ResponseEntity.status(405).body(Map.of(
                "message",
                "지원하지 않는 요청 방식입니다."
        ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> handleUnexpected(Exception e) {
        log.error(
                "Unhandled API exception: {}",
                e.getClass().getName(),
                e
        );
        return ResponseEntity.status(500).body(Map.of(
                "message",
                "서버에서 요청을 처리하는 중 오류가 발생했습니다."
        ));
    }

    private String safeMessage(
            String message,
            String fallback) {
        if (message == null || message.isBlank()) {
            return fallback;
        }
        return message;
    }
}
