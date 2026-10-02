package com.kalima.backend.disputedocument.service;

import com.kalima.backend.audit.AuditAction;
import com.kalima.backend.audit.service.AuditLogService;
import com.kalima.backend.auth.support.CurrentUserService;
import com.kalima.backend.casefile.dto.PartySummary;
import com.kalima.backend.dispute.entity.Dispute;
import com.kalima.backend.dispute.entity.DisputeStage;
import com.kalima.backend.dispute.repository.DisputeRepository;
import com.kalima.backend.dispute.repository.DisputeStageRepository;
import com.kalima.backend.disputedocument.dto.DisputeDocumentResponse;
import com.kalima.backend.disputedocument.entity.DisputeDocument;
import com.kalima.backend.disputedocument.repository.DisputeDocumentRepository;
import com.kalima.backend.domain.entity.Company;
import com.kalima.backend.domain.entity.DomainEnums;
import com.kalima.backend.domain.entity.Person;
import com.kalima.backend.domain.repository.CompanyRepository;
import com.kalima.backend.domain.tenant.TenantContext;
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
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
@Transactional
public class DisputeDocumentService {

    private static final long MAX_FILE_BYTES = 25L * 1024L * 1024L;
    private static final Pattern UNSAFE_FILENAME_CHARS = Pattern.compile("[\\\\/:*?\"<>|\\p{Cntrl}]");

    private final DisputeDocumentRepository documentRepository;
    private final DisputeRepository disputeRepository;
    private final DisputeStageRepository disputeStageRepository;
    private final CompanyRepository companyRepository;
    private final CurrentUserService currentUserService;
    private final AuditLogService auditLogService;
    private final Path storageRoot;

    public DisputeDocumentService(
            DisputeDocumentRepository documentRepository,
            DisputeRepository disputeRepository,
            DisputeStageRepository disputeStageRepository,
            CompanyRepository companyRepository,
            CurrentUserService currentUserService,
            AuditLogService auditLogService,
            @Value("${kalima.disputes.documents.storage-dir:uploads/dispute-documents}")
            String storageDir) {
        this.documentRepository = documentRepository;
        this.disputeRepository = disputeRepository;
        this.disputeStageRepository = disputeStageRepository;
        this.companyRepository = companyRepository;
        this.currentUserService = currentUserService;
        this.auditLogService = auditLogService;
        this.storageRoot = Path.of(storageDir).toAbsolutePath().normalize();
    }

    @Transactional(readOnly = true)
    public java.util.List<DisputeDocumentResponse> list(UUID disputeId, UUID stageIdFilter) {
        loadDispute(disputeId);
        return documentRepository
                .findByDisputeAndOptionalStage(
                        disputeId,
                        stageIdFilter,
                        Sort.by(Sort.Direction.DESC, "createdAt"))
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public DisputeDocumentResponse upload(
            UUID disputeId,
            UUID stageId,
            MultipartFile file,
            DomainEnums.CaseDocumentCategory category,
            DomainEnums.CaseDocumentVisibility visibility,
            String description) {
        Dispute dispute = loadDispute(disputeId);
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "file is required");
        }
        if (file.getSize() > MAX_FILE_BYTES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "file must be 25 MB or smaller");
        }

        DisputeStage stage = null;
        if (stageId != null) {
            stage = disputeStageRepository
                    .findByIdAndDispute_Id(stageId, disputeId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "stage does not belong to this dispute"));
        }

        Person actor = currentUserService.requireCurrentPerson();
        String originalFilename = sanitizeFilename(file.getOriginalFilename());
        String contentType = file.getContentType() == null || file.getContentType().isBlank()
                ? "application/octet-stream"
                : file.getContentType();
        String storedFilename = UUID.randomUUID() + extensionOf(originalFilename);
        Path target = storageRoot
                .resolve(TenantContext.getRequired().toString())
                .resolve(disputeId.toString())
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

        DisputeDocument doc = new DisputeDocument();
        doc.setCompany(currentTenantReference());
        doc.setDispute(dispute);
        doc.setStage(stage);
        doc.setUploadedBy(actor);
        doc.setOriginalFilename(originalFilename);
        doc.setStoredFilename(storedFilename);
        doc.setContentType(contentType);
        doc.setSizeBytes(file.getSize());
        doc.setCategory(category == null ? DomainEnums.CaseDocumentCategory.OTHER : category);
        doc.setVisibility(visibility == null
                ? DomainEnums.CaseDocumentVisibility.STAFF_ONLY
                : visibility);
        doc.setDescription(trimToNull(description));

        DisputeDocument saved = documentRepository.save(doc);
        java.util.Map<String, Object> meta = new java.util.LinkedHashMap<>();
        meta.put("disputeId", disputeId.toString());
        meta.put("filename", saved.getOriginalFilename());
        meta.put("category", saved.getCategory().name());
        meta.put("visibility", saved.getVisibility().name());
        if (stageId != null) {
            meta.put("stageId", stageId.toString());
        }
        auditLogService.recordSuccess(
                AuditAction.DOCUMENT_UPLOADED,
                actor,
                "DisputeDocument",
                saved.getId(),
                meta);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public DownloadableDocument download(UUID disputeId, UUID documentId) {
        DisputeDocument doc = loadDocumentInDispute(disputeId, documentId);
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

    public void delete(UUID disputeId, UUID documentId) {
        DisputeDocument doc = loadDocumentInDispute(disputeId, documentId);
        UUID removedId = doc.getId();
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
                "DisputeDocument",
                removedId,
                Map.of("disputeId", disputeId.toString(), "filename", filename));
    }

    private DisputeDocument loadDocumentInDispute(UUID disputeId, UUID documentId) {
        DisputeDocument doc = documentRepository.findScopedById(documentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Document not found: " + documentId));
        if (!doc.getDispute().getId().equals(disputeId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Document not found for dispute " + disputeId);
        }
        return doc;
    }

    private Dispute loadDispute(UUID disputeId) {
        return disputeRepository.findScopedById(disputeId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Dispute not found: " + disputeId));
    }

    private Company currentTenantReference() {
        return companyRepository.getReferenceById(TenantContext.getRequired());
    }

    private Path filePath(DisputeDocument doc) {
        return storageRoot
                .resolve(doc.getCompany().getId().toString())
                .resolve(doc.getDispute().getId().toString())
                .resolve(doc.getStoredFilename())
                .normalize();
    }

    private DisputeDocumentResponse toResponse(DisputeDocument doc) {
        Person uploadedBy = doc.getUploadedBy();
        return new DisputeDocumentResponse(
                doc.getId(),
                doc.getDispute().getId(),
                doc.getStage() == null ? null : doc.getStage().getId(),
                doc.getOriginalFilename(),
                doc.getContentType(),
                doc.getSizeBytes(),
                doc.getCategory(),
                doc.getVisibility(),
                doc.getDescription(),
                new PartySummary(
                        uploadedBy.getId(),
                        uploadedBy.getFullName(),
                        uploadedBy.getSystemRole()),
                doc.getCreatedAt(),
                doc.getUpdatedAt());
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
