package com.kalima.backend.casedocument.service;

import com.kalima.backend.audit.AuditAction;
import com.kalima.backend.audit.service.AuditLogService;
import com.kalima.backend.auth.support.CurrentUserService;
import com.kalima.backend.casefile.dto.PartySummary;
import com.kalima.backend.casefile.entity.CaseFile;
import com.kalima.backend.casefile.repository.CaseFileRepository;
import com.kalima.backend.casedocument.dto.CaseDocumentResponse;
import com.kalima.backend.casedocument.entity.CaseDocument;
import com.kalima.backend.casedocument.repository.CaseDocumentRepository;
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
public class CaseDocumentService {

    private static final long MAX_FILE_BYTES = 25L * 1024L * 1024L;
    private static final Pattern UNSAFE_FILENAME_CHARS = Pattern.compile("[\\\\/:*?\"<>|\\p{Cntrl}]");

    private final CaseDocumentRepository documentRepository;
    private final CaseFileRepository caseFileRepository;
    private final CompanyRepository companyRepository;
    private final CurrentUserService currentUserService;
    private final AuditLogService auditLogService;
    private final Path storageRoot;

    public CaseDocumentService(CaseDocumentRepository documentRepository,
                               CaseFileRepository caseFileRepository,
                               CompanyRepository companyRepository,
                               CurrentUserService currentUserService,
                               AuditLogService auditLogService,
                               @Value("${kalima.documents.storage-dir:uploads/case-documents}")
                               String storageDir) {
        this.documentRepository = documentRepository;
        this.caseFileRepository = caseFileRepository;
        this.companyRepository = companyRepository;
        this.currentUserService = currentUserService;
        this.auditLogService = auditLogService;
        this.storageRoot = Path.of(storageDir).toAbsolutePath().normalize();
    }

    @Transactional(readOnly = true)
    public java.util.List<CaseDocumentResponse> list(UUID caseId) {
        CaseFile caseFile = loadCase(caseId);
        Person actor = currentUserService.requireCurrentPerson();
        DomainEnums.CaseDocumentVisibility visibility = null;
        if (actor.getSystemRole() == DomainEnums.SystemRole.CLIENT) {
            ensureClientOwnsCase(caseFile, actor);
            visibility = DomainEnums.CaseDocumentVisibility.CLIENT_VISIBLE;
        }
        return documentRepository.findByCaseId(
                        caseId,
                        visibility,
                        Sort.by(Sort.Direction.DESC, "createdAt"))
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public CaseDocumentResponse upload(UUID caseId,
                                       MultipartFile file,
                                       DomainEnums.CaseDocumentCategory category,
                                       DomainEnums.CaseDocumentVisibility visibility,
                                       String description) {
        CaseFile caseFile = loadCase(caseId);
        if (caseFile.getArchivedAt() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Cannot upload documents to an archived case.");
        }
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "file is required");
        }
        if (file.getSize() > MAX_FILE_BYTES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "file must be 25 MB or smaller");
        }

        Person actor = currentUserService.requireCurrentPerson();
        String originalFilename = sanitizeFilename(file.getOriginalFilename());
        String contentType = file.getContentType() == null || file.getContentType().isBlank()
                ? "application/octet-stream"
                : file.getContentType();
        String storedFilename = UUID.randomUUID() + extensionOf(originalFilename);
        Path target = storageRoot
                .resolve(TenantContext.getRequired().toString())
                .resolve(caseId.toString())
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

        CaseDocument doc = new CaseDocument();
        doc.setCompany(currentTenantReference());
        doc.setCaseFile(caseFile);
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

        CaseDocument saved = documentRepository.save(doc);
        auditLogService.recordSuccess(
                AuditAction.DOCUMENT_UPLOADED,
                actor,
                "CaseDocument",
                saved.getId(),
                Map.of(
                        "caseId", caseId.toString(),
                        "filename", saved.getOriginalFilename(),
                        "category", saved.getCategory().name(),
                        "visibility", saved.getVisibility().name()
                ));
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public DownloadableDocument download(UUID caseId, UUID documentId) {
        CaseDocument doc = loadDocumentInCase(caseId, documentId);
        Person actor = currentUserService.requireCurrentPerson();
        if (actor.getSystemRole() == DomainEnums.SystemRole.CLIENT) {
            ensureClientOwnsCase(doc.getCaseFile(), actor);
            if (doc.getVisibility() != DomainEnums.CaseDocumentVisibility.CLIENT_VISIBLE) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Document not found: " + documentId);
            }
        }
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

    public void delete(UUID caseId, UUID documentId) {
        CaseDocument doc = loadDocumentInCase(caseId, documentId);
        Person actor = currentUserService.requireCurrentPerson();
        UUID removedId = doc.getId();
        String filename = doc.getOriginalFilename();
        Path file = filePath(doc);
        documentRepository.delete(doc);
        try {
            Files.deleteIfExists(file);
        } catch (IOException ignored) {
            // Metadata removal is authoritative; a later housekeeping pass can remove orphaned files.
        }
        auditLogService.recordSuccess(
                AuditAction.DOCUMENT_DELETED,
                actor,
                "CaseDocument",
                removedId,
                Map.of("caseId", caseId.toString(), "filename", filename));
    }

    private CaseDocument loadDocumentInCase(UUID caseId, UUID documentId) {
        CaseDocument doc = documentRepository.findScopedById(documentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Document not found: " + documentId));
        if (!doc.getCaseFile().getId().equals(caseId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Document not found for case " + caseId);
        }
        return doc;
    }

    private CaseFile loadCase(UUID caseId) {
        return caseFileRepository.findScopedById(caseId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Case not found: " + caseId));
    }

    private void ensureClientOwnsCase(CaseFile caseFile, Person actor) {
        if (!caseFile.getClient().getId().equals(actor.getId())) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Case not found: " + caseFile.getId());
        }
    }

    private Company currentTenantReference() {
        return companyRepository.getReferenceById(TenantContext.getRequired());
    }

    private Path filePath(CaseDocument doc) {
        return storageRoot
                .resolve(doc.getCompany().getId().toString())
                .resolve(doc.getCaseFile().getId().toString())
                .resolve(doc.getStoredFilename())
                .normalize();
    }

    private CaseDocumentResponse toResponse(CaseDocument doc) {
        Person uploadedBy = doc.getUploadedBy();
        return new CaseDocumentResponse(
                doc.getId(),
                doc.getCaseFile().getId(),
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
