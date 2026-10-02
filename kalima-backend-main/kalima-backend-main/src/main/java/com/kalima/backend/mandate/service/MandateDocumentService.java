package com.kalima.backend.mandate.service;

import com.kalima.backend.audit.AuditAction;
import com.kalima.backend.audit.service.AuditLogService;
import com.kalima.backend.auth.support.CurrentUserService;
import com.kalima.backend.casefile.dto.PartySummary;
import com.kalima.backend.domain.entity.Company;
import com.kalima.backend.domain.entity.DomainEnums;
import com.kalima.backend.domain.entity.Person;
import com.kalima.backend.domain.repository.CompanyRepository;
import com.kalima.backend.domain.tenant.TenantContext;
import com.kalima.backend.mandate.dto.MandateDocumentResponse;
import com.kalima.backend.mandate.entity.Mandate;
import com.kalima.backend.mandate.entity.MandateDocument;
import com.kalima.backend.mandate.repository.MandateDocumentRepository;
import com.kalima.backend.mandate.repository.MandateRepository;
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
public class MandateDocumentService {

    private static final long MAX_FILE_BYTES = 25L * 1024L * 1024L;
    private static final Pattern UNSAFE_FILENAME_CHARS = Pattern.compile("[\\\\/:*?\"<>|\\p{Cntrl}]");

    private final MandateDocumentRepository documentRepository;
    private final MandateRepository mandateRepository;
    private final CompanyRepository companyRepository;
    private final CurrentUserService currentUserService;
    private final AuditLogService auditLogService;
    private final Path storageRoot;

    public MandateDocumentService(
            MandateDocumentRepository documentRepository,
            MandateRepository mandateRepository,
            CompanyRepository companyRepository,
            CurrentUserService currentUserService,
            AuditLogService auditLogService,
            @Value("${kalima.mandates.documents.storage-dir:uploads/mandate-documents}")
            String storageDir) {
        this.documentRepository = documentRepository;
        this.mandateRepository = mandateRepository;
        this.companyRepository = companyRepository;
        this.currentUserService = currentUserService;
        this.auditLogService = auditLogService;
        this.storageRoot = Path.of(storageDir).toAbsolutePath().normalize();
    }

    @Transactional(readOnly = true)
    public List<MandateDocumentResponse> list(UUID mandateId) {
        loadMandate(mandateId);
        return documentRepository
                .findByMandate_Id(mandateId, Sort.by(Sort.Direction.DESC, "createdAt"))
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public MandateDocumentResponse upload(
            UUID mandateId,
            MultipartFile file,
            String description,
            DomainEnums.MandateDocumentType documentType) {
        Mandate mandate = loadMandate(mandateId);
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "file is required");
        }
        if (file.getSize() > MAX_FILE_BYTES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "file must be 25 MB or smaller");
        }

        Person actor = currentUserService.requireCurrentPerson();
        DomainEnums.MandateDocumentType type = documentType == null
                ? DomainEnums.MandateDocumentType.OTHER
                : documentType;
        String originalFilename = sanitizeFilename(file.getOriginalFilename());
        String contentType = file.getContentType() == null || file.getContentType().isBlank()
                ? "application/octet-stream"
                : file.getContentType();
        String storedFilename = UUID.randomUUID() + extensionOf(originalFilename);
        Path target = storageRoot
                .resolve(TenantContext.getRequired().toString())
                .resolve(mandateId.toString())
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

        MandateDocument doc = new MandateDocument();
        doc.setCompany(currentTenantReference());
        doc.setMandate(mandate);
        doc.setUploadedBy(actor);
        doc.setOriginalFilename(originalFilename);
        doc.setStoredFilename(storedFilename);
        doc.setContentType(contentType);
        doc.setSizeBytes(file.getSize());
        doc.setDescription(trimToNull(description));
        doc.setDocumentType(type);

        MandateDocument saved = documentRepository.save(doc);
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("mandateId", mandateId.toString());
        meta.put("filename", saved.getOriginalFilename());
        auditLogService.recordSuccess(
                AuditAction.MANDATE_DOCUMENT_UPLOADED,
                actor,
                "Mandate",
                mandateId,
                meta);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public DownloadableDocument download(UUID mandateId, UUID documentId) {
        MandateDocument doc = loadDocumentInMandate(mandateId, documentId);
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

    public void delete(UUID mandateId, UUID documentId) {
        MandateDocument doc = loadDocumentInMandate(mandateId, documentId);
        String filename = doc.getOriginalFilename();
        Path file = filePath(doc);
        documentRepository.delete(doc);
        try {
            Files.deleteIfExists(file);
        } catch (IOException ignored) {
        }
        auditLogService.recordSuccess(
                AuditAction.MANDATE_DOCUMENT_DELETED,
                currentUserService.requireCurrentPerson(),
                "Mandate",
                mandateId,
                Map.of("mandateId", mandateId.toString(), "filename", filename));
    }

    public void deleteAllForMandate(UUID mandateId) {
        List<MandateDocument> documents = documentRepository.findByMandate_Id(
                mandateId, Sort.unsorted());
        for (MandateDocument document : documents) {
            try {
                Files.deleteIfExists(filePath(document));
            } catch (IOException ignored) {
                // The database row is still removed; orphan cleanup can retry from storage metadata.
            }
        }
        documentRepository.deleteAll(documents);
    }

    private MandateDocument loadDocumentInMandate(UUID mandateId, UUID documentId) {
        MandateDocument doc = documentRepository.findScopedById(documentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Document not found: " + documentId));
        if (!doc.getMandate().getId().equals(mandateId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Document not found for mandate " + mandateId);
        }
        return doc;
    }

    private Mandate loadMandate(UUID mandateId) {
        return mandateRepository.findScopedById(mandateId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "mandate not found"));
    }

    private Company currentTenantReference() {
        return companyRepository.getReferenceById(TenantContext.getRequired());
    }

    private Path filePath(MandateDocument doc) {
        return storageRoot
                .resolve(doc.getCompany().getId().toString())
                .resolve(doc.getMandate().getId().toString())
                .resolve(doc.getStoredFilename())
                .normalize();
    }

    private MandateDocumentResponse toResponse(MandateDocument doc) {
        Person uploadedBy = doc.getUploadedBy();
        return new MandateDocumentResponse(
                doc.getId(),
                doc.getMandate().getId(),
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
