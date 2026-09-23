package com.tardistock.backend.config;

import com.tardistock.backend.security.ImageUploadValidationInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class ImageUploadWebMvcConfig implements WebMvcConfigurer {

    private final ImageUploadValidationInterceptor imageUploadValidationInterceptor;

    public ImageUploadWebMvcConfig(
            ImageUploadValidationInterceptor imageUploadValidationInterceptor) {
        this.imageUploadValidationInterceptor = imageUploadValidationInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(imageUploadValidationInterceptor)
                .addPathPatterns("/api/board/upload");
    }
}
