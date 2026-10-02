package com.kalima.backend.delegation.service;

import com.kalima.backend.audit.AuditAction;
import com.kalima.backend.audit.service.AuditLogService;
import com.kalima.backend.auth.support.CurrentUserService;
import com.kalima.backend.casefile.dto.PartySummary;
import com.kalima.backend.delegation.dto.DelegationDocumentResponse;
import com.kalima.backend.delegation.entity.Delegation;
import com.kalima.backend.delegation.entity.DelegationDocument;
import com.kalima.backend.delegation.repository.DelegationDocumentRepository;
import com.kalima.backend.delegation.repository.DelegationRepository;
import com.kalima.backend.domain.entity.Company;
import com.kalima.backend.domain.entity.DomainEnums;
import com.kalima.backend.domain.entity.Person;
import com.kalima.backend.domain.repository.CompanyRepository;
import com.kalima.backend.domain.tenant.TenantContext;
import com.kalima.backend.ocr.entity.OcrRecord;
import com.kalima.backend.ocr.repository.OcrRecordRepository;
import com.kalima.backend.ocr.service.OcrStorageService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
@Transactional
public class DelegationDocumentService {

    private static final long MAX_FILE_BYTES = 25L * 1024L * 1024L;
    private static final Pattern UNSAFE_FILENAME_CHARS = Pattern.compile("[\\\\/:*?\"<>|\\p{Cntrl}]");

    private final DelegationDocumentRepository documentRepository;
    private final DelegationRepository delegationRepository;
    private final CompanyRepository companyRepository;
    private final CurrentUserService currentUserService;
    private final AuditLogService auditLogService;
    private final OcrRecordRepository ocrRecordRepository;
    private final OcrStorageService ocrStorageService;
    private final Path storageRoot;

    public DelegationDocumentService(
            DelegationDocumentRepository documentRepository,
            DelegationRepository delegationRepository,
            CompanyRepository companyRepository,
            CurrentUserService currentUserService,
            AuditLogService auditLogService,
            OcrRecordRepository ocrRecordRepository,
            OcrStorageService ocrStorageService,
            @Value("${kalima.delegations.documents.storage-dir:uploads/delegation-documents}")
            String storageDir) {
        this.documentRepository = documentRepository;
        this.delegationRepository = delegationRepository;
        this.companyRepository = companyRepository;
        this.currentUserService = currentUserService;
        this.auditLogService = auditLogService;
        this.ocrRecordRepository = ocrRecordRepository;
        this.ocrStorageService = ocrStorageService;
        this.storageRoot = Path.of(storageDir).toAbsolutePath().normalize();
    }

    @Transactional(readOnly = true)
    public List<DelegationDocumentResponse> list(UUID delegationId) {
        loadDelegation(delegationId);
        return documentRepository
                .findByDelegation_Id(delegationId, Sort.by(Sort.Direction.DESC, "createdAt"))
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public DelegationDocumentResponse upload(UUID delegationId, MultipartFile file, String description) {
        return upload(delegationId, file, description, null);
    }

    public DelegationDocumentResponse upload(
            UUID delegationId,
            MultipartFile file,
            String description,
            DomainEnums.DelegationDocumentType documentType) {
        Delegation delegation = loadDelegation(delegationId);
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "file is required");
        }
        if (file.getSize() > MAX_FILE_BYTES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "file must be 25 MB or smaller");
        }

        Person actor = currentUserService.requireCurrentPerson();
        DomainEnums.DelegationDocumentType type = documentType == null
                ? DomainEnums.DelegationDocumentType.OTHER
                : documentType;
        String originalFilename = sanitizeFilename(file.getOriginalFilename());
        if (type == DomainEnums.DelegationDocumentType.ORIGINAL) {
            originalFilename = buildOriginalDisplayFilename(delegation, originalFilename);
        }
        String contentType = file.getContentType() == null || file.getContentType().isBlank()
                ? "application/octet-stream"
                : file.getContentType();
        String storedFilename = UUID.randomUUID() + extensionOf(originalFilename);
        Path target = storageRoot
                .resolve(TenantContext.getRequired().toString())
                .resolve(delegationId.toString())
                .resolve(storedFilename)
                .normalize();
        if (!target.startsWith(storageRoot)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid storage path");
        }

        try {
            Files.createDirectories(target.getParent());
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Could not store uploaded file");
        }

        DelegationDocument doc = new DelegationDocument();
        doc.setCompany(currentTenantReference());
        doc.setDelegation(delegation);
        doc.setUploadedBy(actor);
        doc.setOriginalFilename(originalFilename);
        doc.setStoredFilename(storedFilename);
        doc.setContentType(contentType);
        doc.setSizeBytes(file.getSize());
        doc.setDescription(trimToNull(description));
        doc.setDocumentType(type);

        DelegationDocument saved = documentRepository.save(doc);
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("delegationId", delegationId.toString());
        meta.put("filename", saved.getOriginalFilename());
        auditLogService.recordSuccess(
                AuditAction.DOCUMENT_UPLOADED,
                actor,
                "Delegation",
                delegationId,
                meta);
        return toResponse(saved);
    }

    /**
     * Copies the source file from a completed OCR record into this delegation's
     * document store as {@code ORIGINAL}, avoiding a second upload from the client.
     */
    public DelegationDocumentResponse attachFromOcrRecord(UUID delegationId, UUID ocrRecordId) {
        Delegation delegation = loadDelegation(delegationId);
        OcrRecord ocrRecord = ocrRecordRepository.findScopedById(ocrRecordId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "OCR record not found: " + ocrRecordId));
        if (ocrRecord.getStatus() != DomainEnums.OcrStatus.COMPLETED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "OCR record is not ready for attachment (status=" + ocrRecord.getStatus() + ")");
        }

        Person actor = currentUserService.requireCurrentPerson();
        Path source = ocrStorageService.filePath(ocrRecord);
        if (!Files.isRegularFile(source)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "OCR source file not found for record: " + ocrRecordId);
        }

        String originalFilename = buildOriginalDisplayFilename(
                delegation, sanitizeFilename(ocrRecord.getOriginalFilename()));
        String storedFilename = UUID.randomUUID() + extensionOf(originalFilename);
        Path target = storageRoot
                .resolve(TenantContext.getRequired().toString())
                .resolve(delegationId.toString())
                .resolve(storedFilename)
                .normalize();
        if (!target.startsWith(storageRoot)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid storage path");
        }

        try {
            Files.createDirectories(target.getParent());
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Could not copy OCR file into delegation documents");
        }

        DelegationDocument doc = new DelegationDocument();
        doc.setCompany(currentTenantReference());
        doc.setDelegation(delegation);
        doc.setUploadedBy(actor);
        doc.setOriginalFilename(originalFilename);
        doc.setStoredFilename(storedFilename);
        doc.setContentType(ocrRecord.getContentType());
        doc.setSizeBytes(ocrRecord.getSizeBytes());
        doc.setDocumentType(DomainEnums.DelegationDocumentType.ORIGINAL);

        DelegationDocument saved = documentRepository.save(doc);
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("delegationId", delegationId.toString());
        meta.put("ocrRecordId", ocrRecordId.toString());
        meta.put("filename", saved.getOriginalFilename());
        auditLogService.recordSuccess(
                AuditAction.DOCUMENT_UPLOADED,
                actor,
                "Delegation",
                delegationId,
                meta);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public DownloadableDocument download(UUID delegationId, UUID documentId) {
        DelegationDocument doc = loadDocumentInDelegation(delegationId, documentId);
        Path file = filePath(doc);
        if (!Files.isRegularFile(file)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Stored file not found: " + documentId);
        }
        return new DownloadableDocument(
                new FileSystemResource(file),
                doc.getOriginalFilename(),
                doc.getContentType(),
                doc.getSizeBytes());
    }

    public void delete(UUID delegationId, UUID documentId) {
        DelegationDocument doc = loadDocumentInDelegation(delegationId, documentId);
        String filename = doc.getOriginalFilename();
        Path file = filePath(doc);
        documentRepository.delete(doc);
        try {
            Files.deleteIfExists(file);
        } catch (IOException ignored) {
        }
        auditLogService.recordSuccess(
                AuditAction.DOCUMENT_DELETED,
                currentUserService.requireCurrentPerson(),
                "Delegation",
                delegationId,
                Map.of("delegationId", delegationId.toString(), "filename", filename));
    }

    private DelegationDocument loadDocumentInDelegation(UUID delegationId, UUID documentId) {
        DelegationDocument doc = documentRepository.findScopedById(documentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Document not found: " + documentId));
        if (!doc.getDelegation().getId().equals(delegationId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Document not found for delegation " + delegationId);
        }
        return doc;
    }

    private Delegation loadDelegation(UUID delegationId) {
        return delegationRepository.findScopedById(delegationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "delegation not found"));
    }

    private Company currentTenantReference() {
        return companyRepository.getReferenceById(TenantContext.getRequired());
    }

    private Path filePath(DelegationDocument doc) {
        return storageRoot
                .resolve(doc.getCompany().getId().toString())
                .resolve(doc.getDelegation().getId().toString())
                .resolve(doc.getStoredFilename())
                .normalize();
    }

    private DelegationDocumentResponse toResponse(DelegationDocument doc) {
        Person uploadedBy = doc.getUploadedBy();
        return new DelegationDocumentResponse(
                doc.getId(),
                doc.getDelegation().getId(),
                doc.getOriginalFilename(),
                doc.getContentType(),
                doc.getSizeBytes(),
                doc.getDescription(),
                doc.getDocumentType(),
                new PartySummary(
                        uploadedBy.getId(),
                        displayName(uploadedBy),
                        uploadedBy.getSystemRole()),
                doc.getCreatedAt(),
                doc.getUpdatedAt());
    }

    /**
     * Display filename for ORIGINAL delegation documents: "{client name} - {notary number}".
     * Falls back to the internal delegation number when no notary number is recorded.
     * A hyphen is used instead of the literal "/" separator to keep the name safe for
     * filesystem/HTTP download use; the file itself is always stored under a UUID name.
     */
    private static String buildOriginalDisplayFilename(Delegation delegation, String uploadedFilename) {
        String clientName = sanitizeComponent(displayName(delegation.getClient()));
        String notary = delegation.getNotaryNumber() != null && !delegation.getNotaryNumber().isBlank()
                ? delegation.getNotaryNumber()
                : delegation.getDelegationNumber();
        String base = clientName + " - " + sanitizeComponent(notary);
        String composed = base + extensionOf(uploadedFilename);
        return composed.length() > 255 ? composed.substring(0, 255) : composed;
    }

    private static String sanitizeComponent(String value) {
        if (value == null) {
            return "";
        }
        return UNSAFE_FILENAME_CHARS.matcher(value.trim()).replaceAll("_");
    }

    private static String displayName(Person person) {
        String n = person.getFullName();
        if (n != null && !n.isBlank()) {
            return n.trim();
        }
        if (person.getCompanyName() != null && !person.getCompanyName().isBlank()) {
            return person.getCompanyName().trim();
        }
        return person.getEmail() != null ? person.getEmail() : person.getId().toString();
    }

    private static String sanitizeFilename(String value) {
        String filename = value == null || value.isBlank() ? "document" : value.trim();
        filename = filename.replace('\\', '/');
        int slash = filename.lastIndexOf('/');
        if (slash >= 0) {
            filename = filename.substring(slash + 1);
        }
        filename = UNSAFE_FILENAME_CHARS.matcher(filename).replaceAll("_");
        return filename.length() > 255 ? filename.substring(filename.length() - 255) : filename;
    }

    private static String extensionOf(String filename) {
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return "";
        }
        String ext = filename.substring(dot);
        return ext.length() <= 16 ? ext : "";
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public record DownloadableDocument(
            Resource resource,
            String filename,
            String contentType,
            long sizeBytes
    ) {
    }
}
