package com.kalima.backend.ocr.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kalima.backend.audit.AuditAction;
import com.kalima.backend.audit.service.AuditLogService;
import com.kalima.backend.auth.support.CurrentUserService;
import com.kalima.backend.casedocument.repository.CaseDocumentRepository;
import com.kalima.backend.casefile.service.CaseAccessService;
import com.kalima.backend.delegation.repository.DelegationRepository;
import com.kalima.backend.dispute.repository.DisputeRepository;
import com.kalima.backend.domain.entity.Company;
import com.kalima.backend.domain.entity.DomainEnums;
import com.kalima.backend.domain.entity.Person;
import com.kalima.backend.domain.repository.CompanyRepository;
import com.kalima.backend.domain.repository.PersonRepository;
import com.kalima.backend.ocr.config.OcrProperties;
import com.kalima.backend.ocr.dto.OcrRecordListResponse;
import com.kalima.backend.ocr.dto.OcrRecordResponse;
import com.kalima.backend.ocr.dto.OcrRecordUpdateRequest;
import com.kalima.backend.ocr.dto.OcrRelationInput;
import com.kalima.backend.ocr.dto.OcrRelationResponse;
import com.kalima.backend.ocr.dto.OcrSearchParams;
import com.kalima.backend.ocr.repository.OcrRecordSearchDao;
import com.kalima.backend.ocr.entity.OcrRecord;
import com.kalima.backend.ocr.entity.OcrRelation;
import com.kalima.backend.ocr.repository.OcrRecordRepository;
import com.kalima.backend.ocr.repository.OcrRelationRepository;
import com.kalima.backend.task.repository.TaskItemRepository;
import com.kalima.backend.domain.tenant.TenantContext;
import com.kalima.backend.casefile.dto.PartySummary;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional
public class OcrService {

    private final OcrRecordRepository recordRepository;
    private final OcrRelationRepository relationRepository;
    private final CompanyRepository companyRepository;
    private final CurrentUserService currentUserService;
    private final AuditLogService auditLogService;
    private final OcrAccessService accessService;
    private final OcrStorageService storageService;
    private final OcrProperties properties;
    private final CaseAccessService caseAccessService;
    private final DisputeRepository disputeRepository;
    private final DelegationRepository delegationRepository;
    private final PersonRepository personRepository;
    private final TaskItemRepository taskItemRepository;
    private final CaseDocumentRepository caseDocumentRepository;
    private final ObjectMapper objectMapper;
    private final OcrProcessingService processingService;
    private final OcrRecordSearchDao searchDao;

    public OcrService(OcrRecordRepository recordRepository,
                      OcrRelationRepository relationRepository,
                      CompanyRepository companyRepository,
                      CurrentUserService currentUserService,
                      AuditLogService auditLogService,
                      OcrAccessService accessService,
                      OcrStorageService storageService,
                      OcrProperties properties,
                      CaseAccessService caseAccessService,
                      DisputeRepository disputeRepository,
                      DelegationRepository delegationRepository,
                      PersonRepository personRepository,
                      TaskItemRepository taskItemRepository,
                      CaseDocumentRepository caseDocumentRepository,
                      ObjectMapper objectMapper,
                      OcrProcessingService processingService,
                      OcrRecordSearchDao searchDao) {
        this.recordRepository = recordRepository;
        this.relationRepository = relationRepository;
        this.companyRepository = companyRepository;
        this.currentUserService = currentUserService;
        this.auditLogService = auditLogService;
        this.accessService = accessService;
        this.storageService = storageService;
        this.properties = properties;
        this.caseAccessService = caseAccessService;
        this.disputeRepository = disputeRepository;
        this.delegationRepository = delegationRepository;
        this.personRepository = personRepository;
        this.taskItemRepository = taskItemRepository;
        this.caseDocumentRepository = caseDocumentRepository;
        this.objectMapper = objectMapper;
        this.processingService = processingService;
        this.searchDao = searchDao;
    }

    public OcrRecordResponse upload(MultipartFile file,
                                    String title,
                                    DomainEnums.OcrDocumentType documentType,
                                    String relationsJson) {
        ensureEnabled();
        Person actor = currentUserService.requireCurrentPerson();
        rejectClient(actor);

        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "file is required");
        }

        String contentType = normalizeContentType(file.getContentType());
        try {
            storageService.validateContentType(contentType);
            storageService.validateSize(file.getSize());
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
        }

        UUID recordId = UUID.randomUUID();
        Company company = currentTenantReference();
        String originalFilename = OcrStorageService.sanitizeFilename(file.getOriginalFilename());

        String storedFilename;
        try {
            storedFilename = storageService.storeFromStream(
                    company.getId(),
                    recordId,
                    originalFilename,
                    file.getInputStream());
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Could not store uploaded file");
        }

        OcrRecord record = new OcrRecord();
        record.setId(recordId);
        record.setCompany(company);
        record.setUploadedBy(actor);
        record.setOriginalFilename(originalFilename);
        record.setStoredFilename(storedFilename);
        record.setContentType(contentType);
        record.setSizeBytes(file.getSize());
        record.setDocumentType(documentType == null
                ? DomainEnums.OcrDocumentType.OTHER
                : documentType);
        record.setTitle(resolveTitle(title, originalFilename));
        record.setStatus(DomainEnums.OcrStatus.PENDING);
        record.setAiMetadata(new HashMap<>());

        OcrRecord saved = recordRepository.save(record);
        replaceRelations(saved, parseRelations(relationsJson), actor);

        auditLogService.recordSuccess(
                AuditAction.OCR_UPLOADED,
                actor,
                "OcrRecord",
                saved.getId(),
                Map.of(
                        "filename", saved.getOriginalFilename(),
                        "status", saved.getStatus().name()));

        if (properties.isAutoProcessOnUpload()) {
            processingService.queueProcessing(saved.getId(), actor, false);
            saved = reload(saved.getId());
        }

        return toResponse(saved, true);
    }

    public OcrRecordResponse requestProcess(UUID id, boolean rerun) {
        ensureEnabled();
        Person actor = currentUserService.requireCurrentPerson();
        rejectClient(actor);

        OcrRecord record = loadRecord(id);
        accessService.requireWritable(record, actor);
        processingService.queueProcessing(id, actor, rerun);
        return toResponse(reload(id), true);
    }

    @Transactional(readOnly = true)
    public Page<OcrRecordListResponse> search(OcrSearchParams params, Pageable pageable) {
        ensureEnabled();
        Person actor = currentUserService.requireCurrentPerson();
        rejectClient(actor);

        boolean privileged = accessService.isPrivileged(actor.getSystemRole());
        Page<UUID> idPage = searchDao.searchIds(
                TenantContext.getRequired(),
                actor.getId(),
                privileged,
                params,
                pageable);
        if (idPage.isEmpty()) {
            return Page.empty(pageable);
        }

        List<OcrRecord> loaded = recordRepository.findAllScopedByIds(idPage.getContent());
        Map<UUID, OcrRecord> byId = loaded.stream()
                .collect(Collectors.toMap(OcrRecord::getId, Function.identity()));

        List<OcrRecordListResponse> content = idPage.getContent().stream()
                .map(byId::get)
                .filter(record -> record != null)
                .map(this::toListResponse)
                .toList();

        return new org.springframework.data.domain.PageImpl<>(content, pageable, idPage.getTotalElements());
    }

    @Transactional(readOnly = true)
    public OcrRecordResponse get(UUID id, boolean includeText) {
        ensureEnabled();
        Person actor = currentUserService.requireCurrentPerson();
        rejectClient(actor);

        OcrRecord record = loadRecord(id);
        accessService.requireReadable(record, actor);
        return toResponse(record, includeText);
    }

    public OcrRecordResponse update(UUID id, OcrRecordUpdateRequest request) {
        ensureEnabled();
        Person actor = currentUserService.requireCurrentPerson();
        rejectClient(actor);

        OcrRecord record = loadRecord(id);
        accessService.requireWritable(record, actor);

        if (request.title() != null && !request.title().isBlank()) {
            record.setTitle(request.title().trim());
        }
        if (request.editedText() != null) {
            record.setEditedText(blankToNull(request.editedText()));
        }
        if (request.documentType() != null) {
            record.setDocumentType(request.documentType());
        }
        if (request.relations() != null) {
            replaceRelations(record, request.relations(), actor);
        }

        OcrRecord saved = recordRepository.save(record);
        auditLogService.recordSuccess(
                AuditAction.OCR_UPDATED,
                actor,
                "OcrRecord",
                saved.getId(),
                Map.of("status", saved.getStatus().name()));

        return toResponse(reload(saved.getId()), true);
    }

    public void delete(UUID id) {
        ensureEnabled();
        Person actor = currentUserService.requireCurrentPerson();
        rejectClient(actor);

        OcrRecord record = loadRecord(id);
        if (!accessService.isPrivileged(actor.getSystemRole())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "لا تملك صلاحية حذف سجل OCR.");
        }

        UUID removedId = record.getId();
        String filename = record.getOriginalFilename();
        relationRepository.deleteByOcrRecordId(record.getId());
        recordRepository.delete(record);
        try {
            storageService.deleteRecordFiles(record);
        } catch (IOException ignored) {
            /* metadata removal is authoritative */
        }

        auditLogService.recordSuccess(
                AuditAction.OCR_DELETED,
                actor,
                "OcrRecord",
                removedId,
                Map.of("filename", filename));
    }

    @Transactional(readOnly = true)
    public DownloadableFile downloadOriginal(UUID id) {
        ensureEnabled();
        Person actor = currentUserService.requireCurrentPerson();
        rejectClient(actor);

        OcrRecord record = loadRecord(id);
        accessService.requireReadable(record, actor);

        var path = storageService.filePath(record);
        if (!Files.isRegularFile(path)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Stored file not found: " + id);
        }
        return new DownloadableFile(
                new FileSystemResource(path),
                record.getOriginalFilename(),
                record.getContentType(),
                record.getSizeBytes());
    }

    @Transactional(readOnly = true)
    public DownloadableText downloadText(UUID id) {
        ensureEnabled();
        Person actor = currentUserService.requireCurrentPerson();
        rejectClient(actor);

        OcrRecord record = loadRecord(id);
        accessService.requireReadable(record, actor);

        String text = effectiveText(record);
        if (text == null || text.isBlank()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "No extracted text available yet.");
        }
        String filename = baseName(record.getOriginalFilename()) + "-ocr.txt";
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        return new DownloadableText(bytes, filename);
    }

    private OcrRecord loadRecord(UUID id) {
        return recordRepository.findScopedById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "OCR record not found: " + id));
    }

    private OcrRecord reload(UUID id) {
        return recordRepository.findScopedById(id).orElseThrow();
    }

    private void replaceRelations(OcrRecord record,
                                  List<OcrRelationInput> inputs,
                                  Person actor) {
        relationRepository.deleteByOcrRecordId(record.getId());
        record.getRelations().clear();
        if (inputs == null || inputs.isEmpty()) {
            return;
        }
        Company company = record.getCompany();
        for (OcrRelationInput input : inputs) {
            validateRelationTarget(input, actor);
            OcrRelation relation = new OcrRelation();
            relation.setCompany(company);
            relation.setOcrRecord(record);
            relation.setRelationType(input.relationType());
            relation.setRelationId(input.relationId());
            OcrRelation saved = relationRepository.save(relation);
            record.getRelations().add(saved);
        }
    }

    private void validateRelationTarget(OcrRelationInput input, Person actor) {
        UUID targetId = input.relationId();
        switch (input.relationType()) {
            case CASE -> caseAccessService.requireReadableCase(targetId, actor);
            case DISPUTE -> disputeRepository.findScopedById(targetId)
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND, "Dispute not found: " + targetId));
            case CLIENT -> personRepository.findScopedById(targetId)
                    .filter(p -> p.getSystemRole() == DomainEnums.SystemRole.CLIENT)
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND, "Client not found: " + targetId));
            case TASK -> taskItemRepository.findScopedById(targetId)
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND, "Task not found: " + targetId));
            case DOCUMENT -> caseDocumentRepository.findScopedById(targetId)
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND, "Document not found: " + targetId));
            case DELEGATION -> delegationRepository.findScopedById(targetId)
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND, "Delegation not found: " + targetId));
        }
    }

    private List<OcrRelationInput> parseRelations(String relationsJson) {
        if (relationsJson == null || relationsJson.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(relationsJson, new TypeReference<>() {
            });
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Invalid relations JSON.");
        }
    }

    private OcrRecordListResponse toListResponse(OcrRecord record) {
        Person uploadedBy = record.getUploadedBy();
        List<OcrRelationResponse> relations = mapRelations(record);
        return new OcrRecordListResponse(
                record.getId(),
                record.getTitle(),
                record.getOriginalFilename(),
                record.getContentType(),
                record.getSizeBytes(),
                record.getDocumentType(),
                record.getLanguage(),
                record.getConfidenceScore(),
                record.getStatus(),
                record.getErrorMessage(),
                record.getProvider(),
                new PartySummary(
                        uploadedBy.getId(),
                        uploadedBy.getFullName(),
                        uploadedBy.getSystemRole()),
                record.getProcessingStartedAt(),
                record.getProcessingCompletedAt(),
                relations,
                record.getCreatedAt(),
                record.getUpdatedAt());
    }

    private OcrRecordResponse toResponse(OcrRecord record, boolean includeText) {
        Person uploadedBy = record.getUploadedBy();
        List<OcrRelationResponse> relations = mapRelations(record);

        return new OcrRecordResponse(
                record.getId(),
                record.getTitle(),
                record.getOriginalFilename(),
                record.getContentType(),
                record.getSizeBytes(),
                record.getDocumentType(),
                includeText ? record.getExtractedText() : null,
                includeText ? record.getEditedText() : null,
                record.getLanguage(),
                record.getConfidenceScore(),
                record.getStatus(),
                record.getErrorMessage(),
                record.getProvider(),
                new PartySummary(
                        uploadedBy.getId(),
                        uploadedBy.getFullName(),
                        uploadedBy.getSystemRole()),
                record.getProcessingStartedAt(),
                record.getProcessingCompletedAt(),
                record.getAiMetadata(),
                relations,
                record.getCreatedAt(),
                record.getUpdatedAt());
    }

    private static List<OcrRelationResponse> mapRelations(OcrRecord record) {
        if (record.getRelations() == null) {
            return List.of();
        }
        return record.getRelations().stream()
                .map(rel -> new OcrRelationResponse(
                        rel.getId(),
                        rel.getRelationType(),
                        rel.getRelationId(),
                        rel.getCreatedAt()))
                .toList();
    }

    private static String effectiveText(OcrRecord record) {
        if (record.getEditedText() != null && !record.getEditedText().isBlank()) {
            return record.getEditedText();
        }
        return record.getExtractedText();
    }

    private static String resolveTitle(String title, String originalFilename) {
        if (title != null && !title.isBlank()) {
            String trimmed = title.trim();
            return trimmed.length() > 250 ? trimmed.substring(0, 250) : trimmed;
        }
        return OcrStorageService.defaultTitle(originalFilename);
    }

    private static String normalizeContentType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return "application/octet-stream";
        }
        return contentType.trim().toLowerCase();
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String baseName(String filename) {
        String sanitized = OcrStorageService.sanitizeFilename(filename);
        int dot = sanitized.lastIndexOf('.');
        return dot > 0 ? sanitized.substring(0, dot) : sanitized;
    }

    private Company currentTenantReference() {
        return companyRepository.getReferenceById(TenantContext.getRequired());
    }

    private void ensureEnabled() {
        if (!properties.isEnabled()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "OCR module is disabled.");
        }
    }

    private void rejectClient(Person actor) {
        if (actor.getSystemRole() == DomainEnums.SystemRole.CLIENT) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "OCR is not available to clients.");
        }
    }

    public record DownloadableFile(
            Resource resource,
            String filename,
            String contentType,
            long sizeBytes
    ) {
    }

    public record DownloadableText(byte[] bytes, String filename) {
    }
}
