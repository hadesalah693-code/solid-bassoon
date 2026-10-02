package com.kalima.backend.assistant.service;

import com.kalima.backend.casedocument.entity.CaseDocument;
import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Extracts plain text from stored case documents for RAG. Results are cached
 * in-memory keyed by document id + updatedAt.
 */
@Component
public class CaseDocumentTextExtractor {

    private static final Logger log = LoggerFactory.getLogger(CaseDocumentTextExtractor.class);
    private static final int MAX_EXTRACT_CHARS = 120_000;
    private static final Set<String> PLAIN_TEXT_TYPES = Set.of(
            "text/plain", "text/csv", "application/json");

    private final Path storageRoot;
    private final Tika tika = new Tika();
    private final ConcurrentHashMap<UUID, CachedText> cache = new ConcurrentHashMap<>();

    public CaseDocumentTextExtractor(
            @Value("${kalima.documents.storage-dir:uploads/case-documents}")
            String storageDir) {
        this.storageRoot = Path.of(storageDir).toAbsolutePath().normalize();
    }

    public String extract(CaseDocument doc) {
        if (doc == null) {
            return "";
        }
        UUID cacheKey = doc.getId();
        CachedText cached = cache.get(cacheKey);
        if (cached != null && cached.updatedAt().equals(doc.getUpdatedAt())) {
            return cached.text();
        }
        Path file = filePath(doc);
        if (!Files.isRegularFile(file)) {
            return "";
        }
        String text = readText(doc, file);
        if (text.length() > MAX_EXTRACT_CHARS) {
            text = text.substring(0, MAX_EXTRACT_CHARS) + "…";
        }
        cache.put(cacheKey, new CachedText(doc.getUpdatedAt(), text));
        return text;
    }

    private String readText(CaseDocument doc, Path file) {
        String contentType = doc.getContentType() == null
                ? ""
                : doc.getContentType().toLowerCase(Locale.ROOT);
        String filename = doc.getOriginalFilename() == null
                ? ""
                : doc.getOriginalFilename().toLowerCase(Locale.ROOT);

        if (PLAIN_TEXT_TYPES.contains(contentType) || filename.endsWith(".txt")) {
            try {
                return Files.readString(file, StandardCharsets.UTF_8).trim();
            } catch (IOException e) {
                log.debug("Failed to read text file {}: {}", doc.getId(), e.getMessage());
                return "";
            }
        }

        try (InputStream in = Files.newInputStream(file)) {
            String parsed = tika.parseToString(in);
            return parsed == null ? "" : parsed.trim();
        } catch (IOException | TikaException e) {
            log.debug("Tika extraction skipped for {} ({}): {}",
                    doc.getOriginalFilename(), contentType, e.getMessage());
            return "";
        }
    }

    private Path filePath(CaseDocument doc) {
        return storageRoot
                .resolve(doc.getCompany().getId().toString())
                .resolve(doc.getCaseFile().getId().toString())
                .resolve(doc.getStoredFilename())
                .normalize();
    }

    private record CachedText(java.time.LocalDateTime updatedAt, String text) {
    }
}
