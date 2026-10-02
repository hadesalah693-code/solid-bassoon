package com.kalima.backend.delegation.service;

import com.kalima.backend.audit.AuditAction;
import com.kalima.backend.audit.service.AuditLogService;
import com.kalima.backend.auth.support.CurrentUserService;
import com.kalima.backend.casefile.entity.CaseFile;
import com.kalima.backend.casefile.repository.CaseFileRepository;
import com.kalima.backend.delegation.dto.DelegationCaseLinkResponse;
import com.kalima.backend.delegation.dto.LinkDelegationCaseRequest;
import com.kalima.backend.delegation.entity.Delegation;
import com.kalima.backend.delegation.entity.DelegationCaseLink;
import com.kalima.backend.delegation.repository.DelegationCaseLinkRepository;
import com.kalima.backend.delegation.repository.DelegationRepository;
import com.kalima.backend.domain.entity.Person;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Transactional
public class DelegationCaseLinkService {

    private final DelegationRepository delegationRepository;
    private final DelegationCaseLinkRepository caseLinkRepository;
    private final CaseFileRepository caseFileRepository;
    private final CurrentUserService currentUserService;
    private final AuditLogService auditLogService;

    public DelegationCaseLinkService(DelegationRepository delegationRepository,
                                     DelegationCaseLinkRepository caseLinkRepository,
                                     CaseFileRepository caseFileRepository,
                                     CurrentUserService currentUserService,
                                     AuditLogService auditLogService) {
        this.delegationRepository = delegationRepository;
        this.caseLinkRepository = caseLinkRepository;
        this.caseFileRepository = caseFileRepository;
        this.currentUserService = currentUserService;
        this.auditLogService = auditLogService;
    }

    @Transactional(readOnly = true)
    public List<DelegationCaseLinkResponse> listLinkedCases(UUID delegationId) {
        loadDelegation(delegationId);
        return caseLinkRepository.findByDelegationIdWithCase(delegationId).stream()
                .map(this::toResponse)
                .toList();
    }

    public DelegationCaseLinkResponse linkCase(UUID delegationId, LinkDelegationCaseRequest request) {
        Delegation delegation = loadDelegation(delegationId);
        UUID caseId = request.caseId();
        if (caseLinkRepository.existsByDelegation_IdAndCaseFile_Id(delegationId, caseId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Case is already linked to this delegation");
        }
        CaseFile caseFile = caseFileRepository.findScopedById(caseId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Case not found: " + caseId));

        DelegationCaseLink link = new DelegationCaseLink();
        link.setCompany(delegation.getCompany());
        link.setDelegation(delegation);
        link.setCaseFile(caseFile);

        Person actor = currentUserService.requireCurrentPerson();
        try {
            DelegationCaseLink saved = caseLinkRepository.save(link);
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("delegationId", delegationId.toString());
            meta.put("caseId", caseId.toString());
            meta.put("caseNumber", caseFile.getCaseNumber());
            auditLogService.recordSuccess(
                    AuditAction.DELEGATION_CASE_LINKED,
                    actor,
                    "Delegation",
                    delegationId,
                    meta);
            return toResponse(saved);
        } catch (DataIntegrityViolationException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Case is already linked to this delegation", ex);
        }
    }

    public void unlinkCase(UUID delegationId, UUID caseId) {
        Delegation delegation = loadDelegation(delegationId);
        if (!caseLinkRepository.existsByDelegation_IdAndCaseFile_Id(delegationId, caseId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "No link between delegation and case");
        }
        caseLinkRepository.deleteByDelegation_IdAndCaseFile_Id(delegationId, caseId);
        Person actor = currentUserService.requireCurrentPerson();
        auditLogService.recordSuccess(
                AuditAction.DELEGATION_CASE_UNLINKED,
                actor,
                "Delegation",
                delegationId,
                Map.of("delegationId", delegationId.toString(), "caseId", caseId.toString()));
    }

    private Delegation loadDelegation(UUID delegationId) {
        return delegationRepository.findScopedById(delegationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "delegation not found"));
    }

    private DelegationCaseLinkResponse toResponse(DelegationCaseLink link) {
        CaseFile caseFile = link.getCaseFile();
        return new DelegationCaseLinkResponse(
                link.getId(),
                link.getDelegation().getId(),
                caseFile.getId(),
                caseFile.getCaseNumber(),
                caseFile.getTitle(),
                link.getCreatedAt());
    }
}
