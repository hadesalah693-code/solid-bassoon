package com.kalima.backend.laborcomplaint.service;

import com.kalima.backend.audit.AuditAction;
import com.kalima.backend.audit.service.AuditLogService;
import com.kalima.backend.auth.support.CurrentUserService;
import com.kalima.backend.casefile.dto.PartySummary;
import com.kalima.backend.domain.entity.Company;
import com.kalima.backend.domain.entity.Person;
import com.kalima.backend.domain.repository.CompanyRepository;
import com.kalima.backend.laborcomplaint.dto.LaborComplaintDocumentResponse;
import com.kalima.backend.laborcomplaint.entity.LaborComplaint;
import com.kalima.backend.laborcomplaint.entity.LaborComplaintDocument;
import com.kalima.backend.laborcomplaint.repository.LaborComplaintDocumentRepository;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
@Transactional
public class LaborComplaintDocumentService {

    private static final long MAX_FILE_BYTES = 20L * 1024L * 1024L;
    private static final Pattern UNSAFE_FILENAME_CHARS = Pattern.compile("[\\\\/:*?\"<>|\\p{Cntrl}]");

    private final LaborComplaintDocumentRepository documentRepository;
    private final LaborComplaintService laborComplaintService;
    private final CompanyRepository companyRepository;
    private final CurrentUserService currentUserService;
    private final AuditLogService auditLogService;
    private final Path storageRoot;

    public LaborComplaintDocumentService(LaborComplaintDocumentRepository documentRepository,
                                         LaborComplaintService laborComplaintService,
                                         CompanyRepository companyRepository,
                                         CurrentUserService currentUserService,
                                         AuditLogService auditLogService,
                                         @Value("${kalima.labor-complaints.documents.storage-dir:uploads/labor-complaint-documents}")
                                         String storageDir) {
        this.documentRepository = documentRepository;
        this.laborComplaintService = laborComplaintService;
        this.companyRepository = companyRepository;
        this.currentUserService = currentUserService;
        this.auditLogService = auditLogService;
        this.storageRoot = Path.of(storageDir).toAbsolutePath().normalize();
    }

    @Transactional(readOnly = true)
    public List<LaborComplaintDocumentResponse> list(UUID laborComplaintId) {
        loadComplaint(laborComplaintId);
        return documentRepository.findByLaborComplaint_Id(
                        laborComplaintId,
                        Sort.by(Sort.Direction.DESC, "createdAt"))
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public LaborComplaintDocumentResponse upload(UUID laborComplaintId,
                                                 MultipartFile file,
                                                 String description) {
        LaborComplaint complaint = loadComplaint(laborComplaintId);
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "file is required");
        }
        if (file.getSize() > MAX_FILE_BYTES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "file must be 20 MB or smaller");
        }

        Person actor = currentUserService.requireCurrentPerson();
        String originalFilename = sanitizeFilename(file.getOriginalFilename());
        String storedFilename = UUID.randomUUID() + "_" + originalFilename;
        Path target = storageRoot.resolve(storedFilename);

        try {
            Files.createDirectories(storageRoot);
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "failed to store file");
        }

        LaborComplaintDocument doc = new LaborComplaintDocument();
        doc.setCompany(companyReference(complaint));
        doc.setLaborComplaint(complaint);
        doc.setUploadedBy(actor);
        doc.setOriginalFilename(originalFilename);
        doc.setStoredFilename(storedFilename);
        doc.setContentType(file.getContentType() != null ? file.getContentType() : "application/octet-stream");
        doc.setSizeBytes(file.getSize());
        doc.setDescription(trimToNull(description));

        LaborComplaintDocument saved = documentRepository.save(doc);
        auditLogService.recordSuccess(
                AuditAction.LABOR_COMPLAINT_DOCUMENT_UPLOADED,
                actor,
                "LaborComplaintDocument",
                saved.getId(),
                Map.of("laborComplaintId", laborComplaintId.toString(),
                        "filename", saved.getOriginalFilename()));
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public DownloadableDocument download(UUID laborComplaintId, UUID documentId) {
        loadComplaint(laborComplaintId);
        LaborComplaintDocument doc = documentRepository.findByIdAndLaborComplaint_Id(documentId, laborComplaintId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "document not found"));
        Path path = storageRoot.resolve(doc.getStoredFilename()).normalize();
        if (!path.startsWith(storageRoot) || !Files.isRegularFile(path)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "file missing on disk");
        }
        return new DownloadableDocument(
                doc.getOriginalFilename(),
                doc.getContentType(),
                doc.getSizeBytes(),
                new FileSystemResource(path));
    }

    public void delete(UUID laborComplaintId, UUID documentId) {
        Person actor = currentUserService.requireCurrentPerson();
        loadComplaint(laborComplaintId);
        LaborComplaintDocument doc = documentRepository.findByIdAndLaborComplaint_Id(documentId, laborComplaintId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "document not found"));
        Path path = storageRoot.resolve(doc.getStoredFilename()).normalize();
        documentRepository.delete(doc);
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // best effort
        }
        auditLogService.recordSuccess(
                AuditAction.LABOR_COMPLAINT_DOCUMENT_DELETED,
                actor,
                "LaborComplaintDocument",
                documentId,
                Map.of("laborComplaintId", laborComplaintId.toString()));
    }

    private LaborComplaint loadComplaint(UUID laborComplaintId) {
        return laborComplaintService.requireComplaint(laborComplaintId);
    }

    private Company companyReference(LaborComplaint complaint) {
        return companyRepository.getReferenceById(complaint.getCompany().getId());
    }

    private LaborComplaintDocumentResponse toResponse(LaborComplaintDocument doc) {
        Person uploader = doc.getUploadedBy();
        PartySummary uploadedBy = uploader == null ? null
                : new PartySummary(uploader.getId(), uploader.getFullName(), uploader.getSystemRole());
        return new LaborComplaintDocumentResponse(
                doc.getId(),
                doc.getOriginalFilename(),
                doc.getContentType(),
                doc.getSizeBytes(),
                doc.getDescription(),
                uploadedBy,
                doc.getCreatedAt());
    }

    private static String sanitizeFilename(String original) {
        if (original == null || original.isBlank()) {
            return "upload.bin";
        }
        String name = Path.of(original).getFileName().toString();
        name = UNSAFE_FILENAME_CHARS.matcher(name).replaceAll("_");
        return name.isBlank() ? "upload.bin" : name;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public record DownloadableDocument(
            String filename,
            String contentType,
            long sizeBytes,
            Resource resource
    ) {
    }
}
