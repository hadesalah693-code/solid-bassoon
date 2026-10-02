package com.kalima.backend.caseparticipant.service;

import com.kalima.backend.audit.AuditAction;
import com.kalima.backend.audit.service.AuditLogService;
import com.kalima.backend.auth.support.CurrentUserService;
import com.kalima.backend.casefile.dto.PartySummary;
import com.kalima.backend.caseparticipant.dto.ParticipantAddRequest;
import com.kalima.backend.caseparticipant.dto.ParticipantResponse;
import com.kalima.backend.caseparticipant.entity.CaseParticipant;
import com.kalima.backend.caseparticipant.repository.CaseParticipantRepository;
import com.kalima.backend.casefile.entity.CaseFile;
import com.kalima.backend.domain.entity.Company;
import com.kalima.backend.domain.entity.DomainEnums;
import com.kalima.backend.domain.entity.DomainEnums.CaseParticipantRole;
import com.kalima.backend.domain.entity.DomainEnums.SystemRole;
import com.kalima.backend.domain.entity.Person;
import com.kalima.backend.casefile.repository.CaseFileRepository;
import com.kalima.backend.domain.repository.CompanyRepository;
import com.kalima.backend.domain.repository.PersonRepository;
import com.kalima.backend.domain.tenant.TenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Business logic for {@code case_participants}.
 *
 * <p><b>Role mapping</b> (validated on add): some participant roles restrict
 * which firm {@code SystemRole} the referenced person may have. Unrestricted
 * roles (witnesses, experts, judges, etc.) accept any active person in the
 * tenant.
 *
 * <p><b>Relation to CaseFile</b>: this service never touches
 * {@code cases.client_id} or {@code cases.assigned_lawyer_id}. Those remain
 * the denormalised shortcuts for the primary client/lawyer. Operations on
 * participants are additive and do not cascade back to the parent case.
 */
@Service
@Transactional
public class CaseParticipantService {

    /**
     * Which SystemRoles are acceptable for each CaseParticipantRole.
     * {@code null} value = no restriction (any active tenant person).
     */
    private static final Map<CaseParticipantRole, Set<SystemRole>> ROLE_MATRIX = Map.of(
            CaseParticipantRole.CLIENT,
                Set.of(SystemRole.CLIENT),
            CaseParticipantRole.ASSIGNED_LAWYER,
                EnumSet.of(SystemRole.LAWYER, SystemRole.SENIOR_LAWYER),
            CaseParticipantRole.SECONDARY_LAWYER,
                EnumSet.of(SystemRole.LAWYER, SystemRole.SENIOR_LAWYER)
    );

    private final CaseParticipantRepository participantRepository;
    private final CaseFileRepository caseFileRepository;
    private final PersonRepository personRepository;
    private final CompanyRepository companyRepository;
    private final CurrentUserService currentUserService;
    private final AuditLogService auditLogService;

    public CaseParticipantService(CaseParticipantRepository participantRepository,
                                  CaseFileRepository caseFileRepository,
                                  PersonRepository personRepository,
                                  CompanyRepository companyRepository,
                                  CurrentUserService currentUserService,
                                  AuditLogService auditLogService) {
        this.participantRepository = participantRepository;
        this.caseFileRepository = caseFileRepository;
        this.personRepository = personRepository;
        this.companyRepository = companyRepository;
        this.currentUserService = currentUserService;
        this.auditLogService = auditLogService;
    }

    public ParticipantResponse add(UUID caseId, ParticipantAddRequest request) {
        CaseFile caseFile = loadCase(caseId);
        Person person = loadPerson(request.personId());
        CaseParticipantRole role = request.role();

        validateSystemRoleAllowed(role, person);

        if (participantRepository.existsByCaseAndPersonAndRole(
                caseFile.getId(), person.getId(), role)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Participant already exists for this (case, person, role).");
        }

        Person actor = currentUserService.requireCurrentPerson();

        CaseParticipant entity = new CaseParticipant();
        entity.setCompany(currentTenantReference());
        entity.setCaseFile(caseFile);
        entity.setPerson(person);
        entity.setRole(role);
        entity.setNotes(trimToNull(request.notes()));
        entity.setCreatedBy(actor);

        CaseParticipant saved = participantRepository.save(entity);

        auditLogService.recordSuccess(
                AuditAction.PARTICIPANT_ADDED,
                actor,
                "CaseParticipant", saved.getId(),
                Map.of(
                        "caseId", caseFile.getId().toString(),
                        "personId", person.getId().toString(),
                        "role", role.name()
                ));
        return toResponse(saved);
    }

    /**
     * Internal API used by {@code CaseService} during case creation to seed
     * the primary CLIENT and ASSIGNED_LAWYER participants. Skips duplicates
     * silently so re-runs are idempotent.
     */
    public void addPrimarySeed(CaseFile caseFile,
                               Person person,
                               CaseParticipantRole role,
                               Person createdBy) {
        if (person == null) {
            return;
        }
        if (participantRepository.existsByCaseAndPersonAndRole(
                caseFile.getId(), person.getId(), role)) {
            return;
        }
        CaseParticipant entity = new CaseParticipant();
        entity.setCompany(caseFile.getCompany());
        entity.setCaseFile(caseFile);
        entity.setPerson(person);
        entity.setRole(role);
        entity.setCreatedBy(createdBy);
        participantRepository.save(entity);
    }

    @Transactional(readOnly = true)
    public List<ParticipantResponse> list(UUID caseId) {
        // Load the case first to enforce tenant ownership; otherwise a caller
        // from another tenant could probe caseId and get an empty list which
        // leaks existence information.
        loadCase(caseId);
        return participantRepository.findByCaseId(caseId).stream()
                .map(this::toResponse)
                .toList();
    }

    public void remove(UUID caseId, UUID participantId) {
        CaseParticipant participant = participantRepository
                .findScopedById(participantId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Participant not found: " + participantId));
        if (!participant.getCaseFile().getId().equals(caseId)) {
            // participantId exists but under a different case.
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Participant not found for case " + caseId);
        }

        Map<String, Object> snapshot = Map.of(
                "caseId", participant.getCaseFile().getId().toString(),
                "personId", participant.getPerson().getId().toString(),
                "role", participant.getRole().name());
        UUID removedId = participant.getId();

        participantRepository.delete(participant);
        auditLogService.recordSuccess(
                AuditAction.PARTICIPANT_REMOVED,
                currentUserService.requireCurrentPerson(),
                "CaseParticipant", removedId, snapshot);
    }

    // ----- helpers -------------------------------------------------------

    private CaseFile loadCase(UUID caseId) {
        return caseFileRepository.findScopedById(caseId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Case not found: " + caseId));
    }

    private Person loadPerson(UUID personId) {
        Person person = personRepository.findScopedById(personId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "person not found in this company: " + personId));
        if (!person.isActive()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "person is inactive: " + personId);
        }
        return person;
    }

    private void validateSystemRoleAllowed(CaseParticipantRole role, Person person) {
        Set<SystemRole> allowed = ROLE_MATRIX.get(role);
        if (allowed == null) {
            // Unrestricted role — any active tenant person is acceptable.
            return;
        }
        if (!allowed.contains(person.getSystemRole())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "person systemRole " + person.getSystemRole()
                            + " is not allowed for participant role " + role
                            + " (expected one of " + allowed + ")");
        }
    }

    private Company currentTenantReference() {
        UUID tenantId = TenantContext.getRequired();
        return companyRepository.getReferenceById(tenantId);
    }

    private ParticipantResponse toResponse(CaseParticipant p) {
        Person person = p.getPerson();
        PartySummary party = new PartySummary(
                person.getId(), person.getFullName(), person.getSystemRole());
        return new ParticipantResponse(
                p.getId(),
                p.getCaseFile().getId(),
                party,
                p.getRole(),
                p.getNotes(),
                p.getCreatedAt());
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
