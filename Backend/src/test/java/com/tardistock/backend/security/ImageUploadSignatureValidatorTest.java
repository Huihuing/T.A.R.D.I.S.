package com.tardistock.backend.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.mock.web.MockMultipartHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ImageUploadSignatureValidatorTest {

    @Test
    void acceptsSupportedImageSignatures() throws Exception {
        assertTrue(ImageUploadSignatureValidator.matchesDeclaredImageType(
                file("photo.jpg", "image/jpeg", new byte[] {
                        (byte) 0xff, (byte) 0xd8, (byte) 0xff, (byte) 0xe0
                })));

        assertTrue(ImageUploadSignatureValidator.matchesDeclaredImageType(
                file("image.png", "image/png", new byte[] {
                        (byte) 0x89, 0x50, 0x4e, 0x47,
                        0x0d, 0x0a, 0x1a, 0x0a
                })));

        assertTrue(ImageUploadSignatureValidator.matchesDeclaredImageType(
                file(
                        "animation.gif",
                        "image/gif",
                        "GIF89a".getBytes(StandardCharsets.US_ASCII)
                )));

        assertTrue(ImageUploadSignatureValidator.matchesDeclaredImageType(
                file("image.webp", "image/webp", new byte[] {
                        'R', 'I', 'F', 'F', 0, 0, 0, 0,
                        'W', 'E', 'B', 'P'
                })));
    }

    @Test
    void rejectsMimeTypeThatDoesNotMatchFileSignature() throws Exception {
        assertFalse(ImageUploadSignatureValidator.matchesDeclaredImageType(
                file("fake.png", "image/png", new byte[] {
                        (byte) 0xff, (byte) 0xd8, (byte) 0xff, (byte) 0xe0
                })));
    }

    @Test
    void rejectsNonImagePayloadWithImageMimeType() throws Exception {
        assertFalse(ImageUploadSignatureValidator.matchesDeclaredImageType(
                file(
                        "fake.jpg",
                        "image/jpeg",
                        "not-an-image".getBytes(StandardCharsets.UTF_8)
                )));
    }

    @Test
    void interceptorReturnsBadRequestForSpoofedImage() throws Exception {
        MockMultipartHttpServletRequest request =
                new MockMultipartHttpServletRequest();
        request.addFile(file(
                "fake.png",
                "image/png",
                "plain text".getBytes(StandardCharsets.UTF_8)
        ));
        MockHttpServletResponse response = new MockHttpServletResponse();

        ImageUploadValidationInterceptor interceptor =
                new ImageUploadValidationInterceptor();

        boolean allowed = interceptor.preHandle(
                request,
                response,
                new Object()
        );

        assertFalse(allowed);
        assertEquals(400, response.getStatus());
        assertTrue(response.getContentAsString()
                .contains("실제 이미지 형식"));
    }

    private MockMultipartFile file(
            String filename,
            String contentType,
            byte[] content) {
        return new MockMultipartFile(
                "image",
                filename,
                contentType,
                content
        );
    }
}
