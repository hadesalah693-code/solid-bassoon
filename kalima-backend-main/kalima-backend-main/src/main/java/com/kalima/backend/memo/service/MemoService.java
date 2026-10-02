package com.kalima.backend.memo.service;

import com.kalima.backend.audit.service.AuditLogService;
import com.kalima.backend.auth.support.CurrentUserService;
import com.kalima.backend.casefile.dto.PartySummary;
import com.kalima.backend.casefile.entity.CaseFile;
import com.kalima.backend.casefile.service.CaseAccessService;
import com.kalima.backend.casefile.repository.CaseFileRepository;
import com.kalima.backend.domain.entity.Company;
import com.kalima.backend.domain.entity.DomainEnums;
import com.kalima.backend.domain.entity.Person;
import com.kalima.backend.domain.repository.CompanyRepository;
import com.kalima.backend.domain.tenant.TenantContext;
import com.kalima.backend.memo.dto.MemoResponse;
import com.kalima.backend.memo.dto.MemoWriteRequest;
import com.kalima.backend.memo.entity.Memo;
import com.kalima.backend.memo.entity.MemoStatus;
import com.kalima.backend.memo.entity.MemoType;
import com.kalima.backend.memo.repository.MemoRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Service
@Transactional
public class MemoService {

    private final MemoRepository memoRepository;
    private final CaseFileRepository caseRepository;
    private final CaseAccessService caseAccessService;
    private final CompanyRepository companyRepository;
    private final CurrentUserService currentUserService;
    private final AuditLogService auditLogService;

    public MemoService(
            MemoRepository memoRepository,
            CaseFileRepository caseRepository,
            CaseAccessService caseAccessService,
            CompanyRepository companyRepository,
            CurrentUserService currentUserService,
            AuditLogService auditLogService) {
        this.memoRepository = memoRepository;
        this.caseRepository = caseRepository;
        this.caseAccessService = caseAccessService;
        this.companyRepository = companyRepository;
        this.currentUserService = currentUserService;
        this.auditLogService = auditLogService;
    }

    @Transactional(readOnly = true)
    public Page<MemoResponse> search(
            UUID caseId, MemoStatus status, MemoType memoType, String search, Pageable pageable) {
        Person actor = currentUserService.requireCurrentPerson();
        if (caseId != null) {
            caseAccessService.requireReadableCase(caseId, actor);
        }
        boolean privileged = actor.getSystemRole() == DomainEnums.SystemRole.ADMIN
                || actor.getSystemRole() == DomainEnums.SystemRole.SENIOR_LAWYER;
        String normalized = search == null || search.isBlank() ? null : search.trim();
        return memoRepository.search(TenantContext.getRequired(), caseId, status, memoType,
                normalized, actor.getId(), privileged, pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public MemoResponse get(UUID id) {
        Memo memo = load(id);
        caseAccessService.requireReadableCase(memo.getCaseFile(), currentUserService.requireCurrentPerson());
        return toResponse(memo);
    }

    public MemoResponse create(UUID caseId, MemoWriteRequest request) {
        Person actor = currentUserService.requireCurrentPerson();
        CaseFile caseFile = caseRepository.findScopedById(caseId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Case not found"));
        caseAccessService.requireReadableCase(caseFile, actor);
        if (caseFile.getArchivedAt() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cannot add a memo to an archived case");
        }
        Memo memo = new Memo();
        memo.setCompany(companyRepository.getReferenceById(TenantContext.getRequired()));
        memo.setCaseFile(caseFile);
        memo.setAuthor(actor);
        memo.setTitle(request.title().trim());
        memo.setMemoType(request.memoType());
        memo.setContent(request.content().trim());
        Memo saved = memoRepository.save(memo);
        audit("MEMO_CREATED", actor, saved, Map.of("caseId", caseId.toString()));
        return toResponse(saved);
    }

    public MemoResponse update(UUID id, MemoWriteRequest request) {
        Memo memo = load(id);
        Person actor = currentUserService.requireCurrentPerson();
        requireAuthorOrAdmin(memo, actor);
        requireStatus(memo, MemoStatus.DRAFT);
        memo.setTitle(request.title().trim());
        memo.setMemoType(request.memoType());
        memo.setContent(request.content().trim());
        audit("MEMO_UPDATED", actor, memo, Map.of());
        return toResponse(memo);
    }

    public MemoResponse submit(UUID id) {
        Memo memo = load(id);
        Person actor = currentUserService.requireCurrentPerson();
        requireAuthorOrAdmin(memo, actor);
        requireStatus(memo, MemoStatus.DRAFT);
        memo.setStatus(MemoStatus.UNDER_REVIEW);
        memo.setSubmittedAt(LocalDateTime.now());
        audit("MEMO_SUBMITTED", actor, memo, Map.of());
        return toResponse(memo);
    }

    public MemoResponse approve(UUID id) {
        Memo memo = load(id);
        Person actor = currentUserService.requireCurrentPerson();
        requireStatus(memo, MemoStatus.UNDER_REVIEW);
        memo.setStatus(MemoStatus.APPROVED);
        memo.setReviewer(actor);
        memo.setApprovedAt(LocalDateTime.now());
        audit("MEMO_APPROVED", actor, memo, Map.of());
        return toResponse(memo);
    }

    public MemoResponse send(UUID id) {
        Memo memo = load(id);
        Person actor = currentUserService.requireCurrentPerson();
        requireStatus(memo, MemoStatus.APPROVED);
        memo.setStatus(MemoStatus.SENT);
        memo.setSentAt(LocalDateTime.now());
        audit("MEMO_SENT", actor, memo, Map.of());
        return toResponse(memo);
    }

    public MemoResponse archive(UUID id) {
        Memo memo = load(id);
        Person actor = currentUserService.requireCurrentPerson();
        if (memo.getStatus() == MemoStatus.ARCHIVED) {
            return toResponse(memo);
        }
        memo.setStatus(MemoStatus.ARCHIVED);
        audit("MEMO_ARCHIVED", actor, memo, Map.of());
        return toResponse(memo);
    }

    private Memo load(UUID id) {
        return memoRepository.findScopedById(id, TenantContext.getRequired())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Memo not found"));
    }

    private static void requireStatus(Memo memo, MemoStatus expected) {
        if (memo.getStatus() != expected) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Memo must be " + expected + " to perform this action");
        }
    }

    private static void requireAuthorOrAdmin(Memo memo, Person actor) {
        if (memo.getAuthor().getId().equals(actor.getId())
                || actor.getSystemRole() == DomainEnums.SystemRole.ADMIN
                || actor.getSystemRole() == DomainEnums.SystemRole.SENIOR_LAWYER) {
            return;
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the author may edit this draft");
    }

    private void audit(String action, Person actor, Memo memo, Map<String, Object> metadata) {
        auditLogService.recordSuccess(action, actor, "Memo", memo.getId(), metadata);
    }

    private MemoResponse toResponse(Memo memo) {
        return new MemoResponse(memo.getId(), memo.getCaseFile().getId(),
                memo.getCaseFile().getCaseNumber(), memo.getCaseFile().getTitle(),
                memo.getTitle(), memo.getMemoType(), memo.getStatus(), toParty(memo.getAuthor()),
                toParty(memo.getReviewer()), memo.getContent(), memo.getSubmittedAt(),
                memo.getApprovedAt(), memo.getSentAt(), memo.getCreatedAt(), memo.getUpdatedAt());
    }

    private static PartySummary toParty(Person person) {
        return person == null ? null : new PartySummary(person.getId(), person.getFullName(), person.getSystemRole());
    }
}