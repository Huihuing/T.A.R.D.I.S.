package com.tardistock.backend.security;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

public final class ImageUploadSignatureValidator {

    private static final int HEADER_LENGTH = 12;

    private ImageUploadSignatureValidator() {
    }

    public static boolean matchesDeclaredImageType(
            MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            return false;
        }

        String contentType = file.getContentType();
        if (contentType == null || contentType.isBlank()) {
            return false;
        }

        byte[] header;
        try (InputStream input = file.getInputStream()) {
            header = input.readNBytes(HEADER_LENGTH);
        }

        return switch (contentType.toLowerCase(Locale.ROOT)) {
            case "image/jpeg" -> isJpeg(header);
            case "image/png" -> isPng(header);
            case "image/gif" -> isGif(header);
            case "image/webp" -> isWebp(header);
            default -> false;
        };
    }

    private static boolean isJpeg(byte[] header) {
        return header.length >= 3
                && header[0] == (byte) 0xff
                && header[1] == (byte) 0xd8
                && header[2] == (byte) 0xff;
    }

    private static boolean isPng(byte[] header) {
        byte[] signature = new byte[] {
                (byte) 0x89, 0x50, 0x4e, 0x47,
                0x0d, 0x0a, 0x1a, 0x0a
        };
        return startsWith(header, signature);
    }

    private static boolean isGif(byte[] header) {
        return asciiAt(header, 0, "GIF87a")
                || asciiAt(header, 0, "GIF89a");
    }

    private static boolean isWebp(byte[] header) {
        return asciiAt(header, 0, "RIFF")
                && asciiAt(header, 8, "WEBP");
    }

    private static boolean startsWith(byte[] value, byte[] prefix) {
        if (value.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (value[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    private static boolean asciiAt(
            byte[] value,
            int offset,
            String expected) {
        byte[] signature = expected.getBytes(StandardCharsets.US_ASCII);
        if (offset < 0 || value.length < offset + signature.length) {
            return false;
        }
        for (int i = 0; i < signature.length; i++) {
            if (value[offset + i] != signature[i]) {
                return false;
            }
        }
        return true;
    }
}
