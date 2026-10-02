package com.kalima.backend.message.support;

import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

public final class MessageAttachmentValidation {

    public static final String PUBLIC_URL_PREFIX = "/api/messages/attachments/";
    public static final long MAX_BYTES = 25L * 1024L * 1024L;

    private static final Pattern UNSAFE = Pattern.compile("[\\\\/:*?\"<>|\\p{Cntrl}]");
    private static final Pattern STORED_FILENAME =
            Pattern.compile("^[0-9a-fA-F-]{36}_[^\\\\/]+$");

    private static final Set<String> ALLOWED_CONTENT_TYPE_PREFIXES = Set.of(
            "image/jpeg", "image/png", "image/gif", "image/webp",
            "audio/webm", "audio/mpeg", "audio/ogg", "audio/wav", "audio/mp4",
            "video/webm",
            "application/pdf",
            "application/msword",
            "application/vnd.openxmlformats-officedocument",
            "text/plain");

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            ".jpg", ".jpeg", ".png", ".gif", ".webp",
            ".webm", ".mp3", ".ogg", ".wav", ".m4a",
            ".pdf", ".doc", ".docx", ".txt");

    private static final Set<String> BLOCKED_EXTENSIONS = Set.of(
            ".html", ".htm", ".svg", ".js", ".mjs", ".jsx", ".ts", ".tsx",
            ".exe", ".bat", ".cmd", ".com", ".msi", ".dll",
            ".sh", ".bash", ".ps1", ".vbs", ".jar", ".war",
            ".php", ".jsp", ".asp", ".aspx");

    private MessageAttachmentValidation() {
    }

    public static void validateUpload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw badRequest("File must not be empty");
        }
        if (file.getSize() > MAX_BYTES) {
            throw payloadTooLarge("File exceeds 25 MB limit");
        }

        String contentType = file.getContentType();
        if (contentType == null || contentType.isBlank()) {
            throw badRequest("File type not allowed");
        }
        String normalizedType = contentType.toLowerCase(Locale.ROOT).trim();
        if (ALLOWED_CONTENT_TYPE_PREFIXES.stream().noneMatch(normalizedType::startsWith)) {
            throw badRequest("File type not allowed");
        }

        String extension = extensionOf(file.getOriginalFilename());
        if (extension.isEmpty()) {
            throw badRequest("File extension is required");
        }
        if (BLOCKED_EXTENSIONS.contains(extension)) {
            throw badRequest("File type not allowed");
        }
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw badRequest("File type not allowed");
        }
    }

    public static String sanitizeOriginalFilename(String originalFilename) {
        String name = originalFilename != null && !originalFilename.isBlank()
                ? originalFilename.trim()
                : "file";
        return UNSAFE.matcher(name).replaceAll("_");
    }

    public static String validateStoredFilenameParam(String filename) {
        if (filename == null || filename.isBlank()
                || filename.contains("..")
                || UNSAFE.matcher(filename).find()
                || !STORED_FILENAME.matcher(filename).matches()) {
            throw badRequest("Invalid filename");
        }
        String extension = extensionOf(filename);
        if (BLOCKED_EXTENSIONS.contains(extension)) {
            throw notFound("File not found");
        }
        return filename;
    }

    public static String extractStoredFilenameFromUrl(String attachmentUrl) {
        if (attachmentUrl == null || attachmentUrl.isBlank()) {
            throw badRequest("Invalid attachment URL");
        }
        String trimmed = attachmentUrl.trim();
        if (trimmed.contains("..")
                || trimmed.startsWith("http://")
                || trimmed.startsWith("https://")
                || !trimmed.startsWith(PUBLIC_URL_PREFIX)) {
            throw badRequest("Invalid attachment URL");
        }
        return validateStoredFilenameParam(trimmed.substring(PUBLIC_URL_PREFIX.length()));
    }

    private static String extensionOf(String filename) {
        if (filename == null) {
            return "";
        }
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return "";
        }
        return filename.substring(dot).toLowerCase(Locale.ROOT);
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private static ResponseStatusException payloadTooLarge(String message) {
        return new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, message);
    }

    private static ResponseStatusException notFound(String message) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
    }
}
