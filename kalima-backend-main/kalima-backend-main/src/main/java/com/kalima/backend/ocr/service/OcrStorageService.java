package com.kalima.backend.ocr.service;

import com.kalima.backend.ocr.config.OcrProperties;
import com.kalima.backend.ocr.entity.OcrRecord;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Component
public class OcrStorageService {

    private static final Pattern UNSAFE_FILENAME_CHARS = Pattern.compile("[\\\\/:*?\"<>|\\p{Cntrl}]");

    private final Path storageRoot;
    private final OcrProperties properties;

    public OcrStorageService(OcrProperties properties) {
        this.properties = properties;
        this.storageRoot = Path.of(properties.getStorageDir()).toAbsolutePath().normalize();
    }

    public void validateContentType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            throw new IllegalArgumentException("content type is required");
        }
        Set<String> allowed = properties.allowedContentTypeSet();
        String normalized = contentType.trim().toLowerCase();
        if (!allowed.contains(normalized)) {
            throw new IllegalArgumentException(
                    "Unsupported file type. Allowed: JPG, JPEG, PNG, PDF.");
        }
    }

    public void validateSize(long sizeBytes) {
        if (sizeBytes <= 0) {
            throw new IllegalArgumentException("file is empty");
        }
        if (sizeBytes > properties.getMaxFileBytes()) {
            throw new IllegalArgumentException("file must be 25 MB or smaller");
        }
    }

    public String storeFromStream(UUID companyId,
                                  UUID recordId,
                                  String originalFilename,
                                  InputStream inputStream) throws IOException {
        String sanitized = sanitizeFilename(originalFilename);
        String storedFilename = UUID.randomUUID() + extensionOf(sanitized);
        Path target = resolvePath(companyId, recordId, storedFilename);
        Files.createDirectories(target.getParent());
        Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
        return storedFilename;
    }

    public Path resolvePath(UUID companyId, UUID recordId, String storedFilename) {
        Path target = storageRoot
                .resolve(companyId.toString())
                .resolve(recordId.toString())
                .resolve(storedFilename)
                .normalize();
        if (!target.startsWith(storageRoot)) {
            throw new IllegalArgumentException("Invalid storage path");
        }
        return target;
    }

    public Path filePath(OcrRecord record) {
        return resolvePath(
                record.getCompany().getId(),
                record.getId(),
                record.getStoredFilename());
    }

    public void deleteRecordFiles(OcrRecord record) throws IOException {
        Path dir = storageRoot
                .resolve(record.getCompany().getId().toString())
                .resolve(record.getId().toString())
                .normalize();
        if (!dir.startsWith(storageRoot) || !Files.isDirectory(dir)) {
            return;
        }
        try (var stream = Files.list(dir)) {
            stream.forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    /* best effort */
                }
            });
        }
        Files.deleteIfExists(dir);
    }

    public static String sanitizeFilename(String value) {
        String filename = value == null || value.isBlank() ? "document" : value.trim();
        filename = filename.replace('\\', '/');
        int slash = filename.lastIndexOf('/');
        if (slash >= 0) {
            filename = filename.substring(slash + 1);
        }
        filename = UNSAFE_FILENAME_CHARS.matcher(filename).replaceAll("_");
        return filename.length() > 255 ? filename.substring(filename.length() - 255) : filename;
    }

    public static String extensionOf(String filename) {
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return "";
        }
        String ext = filename.substring(dot).toLowerCase();
        return ext.length() <= 16 ? ext : "";
    }

    public static String defaultTitle(String originalFilename) {
        String sanitized = sanitizeFilename(originalFilename);
        int dot = sanitized.lastIndexOf('.');
        if (dot <= 0) {
            return sanitized.length() > 250 ? sanitized.substring(0, 250) : sanitized;
        }
        String base = sanitized.substring(0, dot).trim();
        if (base.isEmpty()) {
            return sanitized;
        }
        return base.length() > 250 ? base.substring(0, 250) : base;
    }
}
