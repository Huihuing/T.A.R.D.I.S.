package com.tardistock.backend.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
public class ImageUploadValidationInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler) throws IOException {
        if (!(request instanceof MultipartHttpServletRequest multipartRequest)) {
            return true;
        }

        MultipartFile image = multipartRequest.getFile("image");
        if (image == null || image.isEmpty()) {
            return true;
        }

        if (!ImageUploadSignatureValidator.matchesDeclaredImageType(image)) {
            writeInvalidImageResponse(response);
            return false;
        }

        return true;
    }

    private void writeInvalidImageResponse(
            HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(
                "{\"message\":\"실제 이미지 형식과 파일 유형이 일치하지 않습니다.\"}"
        );
    }
}
