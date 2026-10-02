package com.kalima.backend.casefile.service;

import com.kalima.backend.audit.service.AuditLogService;
import com.kalima.backend.auth.support.CurrentUserService;
import com.kalima.backend.casefile.dto.CaseInternalNoteCreateRequest;
import com.kalima.backend.casefile.dto.CaseInternalNoteResponse;
import com.kalima.backend.casefile.dto.PartySummary;
import com.kalima.backend.casefile.entity.CaseFile;
import com.kalima.backend.casefile.entity.CaseInternalNote;
import com.kalima.backend.casefile.repository.CaseFileRepository;
import com.kalima.backend.casefile.repository.CaseInternalNoteRepository;
import com.kalima.backend.domain.entity.Person;
import com.kalima.backend.domain.repository.CompanyRepository;
import com.kalima.backend.domain.tenant.TenantContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.UUID;

@Service
@Transactional
public class CaseInternalNoteService {

    private final CaseFileRepository caseRepository;
    private final CaseInternalNoteRepository noteRepository;
    private final CompanyRepository companyRepository;
    private final CurrentUserService currentUserService;
    private final AuditLogService auditLogService;

    public CaseInternalNoteService(
            CaseFileRepository caseRepository,
            CaseInternalNoteRepository noteRepository,
            CompanyRepository companyRepository,
            CurrentUserService currentUserService,
            AuditLogService auditLogService) {
        this.caseRepository = caseRepository;
        this.noteRepository = noteRepository;
        this.companyRepository = companyRepository;
        this.currentUserService = currentUserService;
        this.auditLogService = auditLogService;
    }

    @Transactional(readOnly = true)
    public Page<CaseInternalNoteResponse> list(UUID caseId, Pageable pageable) {
        requireCase(caseId);
        return noteRepository.findForCase(caseId, pageable).map(this::toResponse);
    }

    public CaseInternalNoteResponse create(UUID caseId, CaseInternalNoteCreateRequest request) {
        CaseFile caseFile = requireCase(caseId);
        if (caseFile.getArchivedAt() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Cannot add an internal note to an archived case");
        }
        Person actor = currentUserService.requireCurrentPerson();
        CaseInternalNote note = new CaseInternalNote();
        note.setCompany(companyRepository.getReferenceById(TenantContext.getRequired()));
        note.setCaseFile(caseFile);
        note.setAuthor(actor);
        note.setContent(request.content().trim());
        CaseInternalNote saved = noteRepository.save(note);
        auditLogService.recordSuccess("CASE_INTERNAL_NOTE_ADDED", actor,
                "CaseInternalNote", saved.getId(), Map.of("caseId", caseId.toString()));
        return toResponse(saved);
    }

    private CaseFile requireCase(UUID id) {
        return caseRepository.findScopedById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Case not found"));
    }

    private CaseInternalNoteResponse toResponse(CaseInternalNote note) {
        Person author = note.getAuthor();
        return new CaseInternalNoteResponse(note.getId(), note.getContent(),
                new PartySummary(author.getId(), author.getFullName(), author.getSystemRole()),
                note.getCreatedAt());
    }
}