package com.kalima.backend.message.service;

import com.kalima.backend.auth.support.CurrentUserService;
import com.kalima.backend.casefile.entity.CaseFile;
import com.kalima.backend.casefile.repository.CaseFileRepository;
import com.kalima.backend.casefile.service.CaseAccessService;
import com.kalima.backend.domain.entity.Company;
import com.kalima.backend.domain.entity.MessageAttachment;
import com.kalima.backend.domain.entity.Person;
import com.kalima.backend.domain.repository.CompanyRepository;
import com.kalima.backend.domain.tenant.TenantContext;
import com.kalima.backend.message.repository.MessageAttachmentRepository;
import com.kalima.backend.message.support.MessageAttachmentValidation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Service
@Transactional
public class MessageAttachmentService {

    private final Path storageRoot;
    private final MessageAttachmentRepository attachmentRepository;
    private final CaseFileRepository caseFileRepository;
    private final CompanyRepository companyRepository;
    private final CaseAccessService caseAccessService;
    private final CurrentUserService currentUserService;

    public MessageAttachmentService(
            @Value("${kalima.documents.storage-dir:uploads/case-documents}") String baseDir,
            MessageAttachmentRepository attachmentRepository,
            CaseFileRepository caseFileRepository,
            CompanyRepository companyRepository,
            CaseAccessService caseAccessService,
            CurrentUserService currentUserService) {
        this.storageRoot = Path.of(baseDir, "message-attachments").toAbsolutePath().normalize();
        this.attachmentRepository = attachmentRepository;
        this.caseFileRepository = caseFileRepository;
        this.companyRepository = companyRepository;
        this.caseAccessService = caseAccessService;
        this.currentUserService = currentUserService;
    }

    public record DownloadResult(Resource resource, String originalFilename, String contentType) {}

    public String upload(UUID caseId, MultipartFile file) {
        CaseFile caseFile = loadCase(caseId);
        if (caseFile.getArchivedAt() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Cannot upload attachments to an archived case.");
        }
        Person actor = currentUserService.requireCurrentPerson();
        caseAccessService.requireSendableCase(caseFile, actor);
        MessageAttachmentValidation.validateUpload(file);

        String originalFilename = MessageAttachmentValidation.sanitizeOriginalFilename(
                file.getOriginalFilename());
        String storedFilename = UUID.randomUUID() + "_" + originalFilename;
        String contentType = file.getContentType().trim();

        Path target = filePath(TenantContext.getRequired(), caseId, storedFilename);
        try {
            Files.createDirectories(target.getParent());
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Could not store file");
        }

        MessageAttachment attachment = new MessageAttachment();
        attachment.setCompany(currentTenantReference());
        attachment.setCaseFile(caseFile);
        attachment.setUploadedBy(actor);
        attachment.setOriginalFilename(originalFilename);
        attachment.setStoredFilename(storedFilename);
        attachment.setContentType(contentType);
        attachment.setSizeBytes(file.getSize());
        attachmentRepository.save(attachment);

        return MessageAttachmentValidation.PUBLIC_URL_PREFIX + storedFilename;
    }

    @Transactional(readOnly = true)
    public DownloadResult download(String filename) {
        String storedFilename = MessageAttachmentValidation.validateStoredFilenameParam(filename);
        Person actor = currentUserService.requireCurrentPerson();
        MessageAttachment attachment = attachmentRepository.findScopedByStoredFilename(storedFilename)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "File not found"));
        caseAccessService.requireReadableCase(
                attachment.getCaseFile().getId(),
                actor.getId(),
                actor.getSystemRole());

        Path file = filePath(
                attachment.getCompany().getId(),
                attachment.getCaseFile().getId(),
                attachment.getStoredFilename());
        if (!Files.isRegularFile(file)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "File not found");
        }
        return new DownloadResult(
                new FileSystemResource(file),
                attachment.getOriginalFilename(),
                attachment.getContentType());
    }

    @Transactional(readOnly = true)
    public String validateAttachmentUrlForSend(UUID caseId, String attachmentUrl, Person actor) {
        String storedFilename = MessageAttachmentValidation.extractStoredFilenameFromUrl(attachmentUrl);
        MessageAttachment attachment = attachmentRepository.findScopedByStoredFilename(storedFilename)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Invalid attachment URL"));
        if (!attachment.getCaseFile().getId().equals(caseId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid attachment URL");
        }
        if (!attachment.getUploadedBy().getId().equals(actor.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid attachment URL");
        }
        return MessageAttachmentValidation.PUBLIC_URL_PREFIX + storedFilename;
    }

    private CaseFile loadCase(UUID caseId) {
        return caseFileRepository.findScopedById(caseId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Case not found: " + caseId));
    }

    private Company currentTenantReference() {
        return companyRepository.getReferenceById(TenantContext.getRequired());
    }

    private Path filePath(UUID companyId, UUID caseId, String storedFilename) {
        Path target = storageRoot
                .resolve(companyId.toString())
                .resolve(caseId.toString())
                .resolve(storedFilename)
                .normalize();
        if (!target.startsWith(storageRoot)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid storage path");
        }
        return target;
    }
}
