package com.kalima.backend.laborcomplaint.service;

import com.kalima.backend.audit.AuditAction;
import com.kalima.backend.audit.service.AuditLogService;
import com.kalima.backend.auth.support.CurrentUserService;
import com.kalima.backend.casefile.dto.PartySummary;
import com.kalima.backend.casefile.entity.CaseFile;
import com.kalima.backend.casefile.repository.CaseFileRepository;
import com.kalima.backend.dispute.dto.DisputeTeamMemberResponse;
import com.kalima.backend.dispute.entity.Dispute;
import com.kalima.backend.dispute.repository.DisputeRepository;
import com.kalima.backend.dispute.service.DisputeTeamService;
import com.kalima.backend.domain.entity.Company;
import com.kalima.backend.domain.entity.DomainEnums;
import com.kalima.backend.domain.entity.Person;
import com.kalima.backend.domain.repository.CompanyRepository;
import com.kalima.backend.domain.repository.PersonRepository;
import com.kalima.backend.domain.tenant.TenantContext;
import com.kalima.backend.laborcomplaint.dto.LaborComplaintClientInput;
import com.kalima.backend.laborcomplaint.dto.LaborComplaintClientResponse;
import com.kalima.backend.laborcomplaint.dto.LaborComplaintCreateRequest;
import com.kalima.backend.laborcomplaint.dto.LaborComplaintDisputeTeamSnapshot;
import com.kalima.backend.laborcomplaint.dto.LaborComplaintListItemResponse;
import com.kalima.backend.laborcomplaint.dto.LaborComplaintMolSessionInput;
import com.kalima.backend.laborcomplaint.dto.LaborComplaintMolSessionResponse;
import com.kalima.backend.laborcomplaint.dto.LaborComplaintOpponentInput;
import com.kalima.backend.laborcomplaint.dto.LaborComplaintOpponentResponse;
import com.kalima.backend.laborcomplaint.dto.LaborComplaintResponse;
import com.kalima.backend.laborcomplaint.dto.LaborComplaintStatusUpdateRequest;
import com.kalima.backend.laborcomplaint.dto.LaborComplaintUpdateRequest;
import com.kalima.backend.laborcomplaint.entity.LaborComplaint;
import com.kalima.backend.laborcomplaint.entity.LaborComplaintMolSession;
import com.kalima.backend.laborcomplaint.entity.LaborComplaintParty;
import com.kalima.backend.laborcomplaint.repository.LaborComplaintNumberSequenceRepository;
import com.kalima.backend.laborcomplaint.repository.LaborComplaintRepository;
import com.kalima.backend.laborcomplaint.repository.LaborComplaintSpecifications;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class LaborComplaintService {

    private static final Set<DomainEnums.SystemRole> LAWYER_ROLES = EnumSet.of(
            DomainEnums.SystemRole.LAWYER,
            DomainEnums.SystemRole.SENIOR_LAWYER);

    private final LaborComplaintRepository repository;
    private final LaborComplaintNumberSequenceRepository numberSequenceRepository;
    private final PersonRepository personRepository;
    private final CompanyRepository companyRepository;
    private final CaseFileRepository caseFileRepository;
    private final DisputeRepository disputeRepository;
    private final DisputeTeamService disputeTeamService;
    private final CurrentUserService currentUserService;
    private final AuditLogService auditLogService;

    public LaborComplaintService(LaborComplaintRepository repository,
                                 LaborComplaintNumberSequenceRepository numberSequenceRepository,
                                 PersonRepository personRepository,
                                 CompanyRepository companyRepository,
                                 CaseFileRepository caseFileRepository,
                                 DisputeRepository disputeRepository,
                                 DisputeTeamService disputeTeamService,
                                 CurrentUserService currentUserService,
                                 AuditLogService auditLogService) {
        this.repository = repository;
        this.numberSequenceRepository = numberSequenceRepository;
        this.personRepository = personRepository;
        this.companyRepository = companyRepository;
        this.caseFileRepository = caseFileRepository;
        this.disputeRepository = disputeRepository;
        this.disputeTeamService = disputeTeamService;
        this.currentUserService = currentUserService;
        this.auditLogService = auditLogService;
    }

    public LaborComplaintResponse create(LaborComplaintCreateRequest request) {
        Person actor = currentUserService.requireCurrentPerson();
        LocalDate today = LocalDate.now(ZoneId.systemDefault());
        LocalDate registrationDate = request.registrationDate() != null ? request.registrationDate() : today;
        LocalDate receiptDate = request.receiptDate() != null ? request.receiptDate() : today;
        int year = request.complaintYear() != null ? request.complaintYear() : registrationDate.getYear();

        List<LaborComplaintClientInput> clientInputs = resolveClientInputs(request.clients(), request.clientId());
        List<LaborComplaintOpponentInput> opponents = resolveOpponentInputs(
                request.opponents(), request.opponentName(), request.opponentCapacity());
        Person lawyer = resolveLawyer(request.assignedLawyerId());
        Dispute linkedDispute = resolveDispute(request.linkedDisputeId());

        UUID tenantId = TenantContext.getRequired();
        String complaintNumber = resolveComplaintNumber(request.complaintNumber(), year, tenantId, null);
        year = resolveComplaintYear(complaintNumber, year);

        LaborComplaint entity = new LaborComplaint();
        entity.setCompany(currentTenantReference());
        entity.setComplaintNumber(complaintNumber);
        entity.setComplaintYear(year);
        entity.setRegistrationDate(registrationDate);
        entity.setReceiptDate(receiptDate);
        entity.setSubject(trimToNull(request.subject()));
        entity.setSummary(trimToNull(request.summary()));
        entity.setLegalRequest(trimToNull(request.legalRequest()));
        entity.setStatus(DomainEnums.LaborComplaintStatus.ACTIVE);
        entity.setAssignedLawyer(lawyer);
        entity.setLinkedDispute(linkedDispute);
        entity.setInternalReference(trimToNull(request.internalReference()));
        entity.setExternalPartners(trimToNull(request.externalPartners()));
        entity.setOpponentRepresentative(trimToNull(request.opponentRepresentative()));
        entity.setCooperatingOffices(trimToNull(request.cooperatingOffices()));
        entity.setCreatedBy(actor);
        applyClientParties(entity, clientInputs);
        applyOpponentParties(entity, opponents);
        syncLegacyPartyFields(entity);
        applyMolSessions(entity, request.molSessions());

        LaborComplaint saved = repository.save(entity);
        auditLogService.recordSuccess(
                AuditAction.LABOR_COMPLAINT_CREATED,
                actor,
                "LaborComplaint",
                saved.getId(),
                Map.of("complaintNumber", saved.getComplaintNumber(),
                        "clientId", clientInputs.get(0).clientId().toString()));
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public Page<LaborComplaintListItemResponse> search(
            DomainEnums.LaborComplaintStatus status,
            UUID clientId,
            UUID assignedLawyerId,
            String search,
            Pageable pageable) {
        String normalizedSearch = search == null || search.isBlank() ? null : search.trim();
        return repository.findAll(
                        LaborComplaintSpecifications.filtered(status, clientId, assignedLawyerId, normalizedSearch),
                        pageable)
                .map(this::toListItem);
    }

    @Transactional(readOnly = true)
    public LaborComplaintResponse findById(UUID id) {
        return toResponse(requireComplaint(id));
    }

    public LaborComplaintResponse update(UUID id, LaborComplaintUpdateRequest request) {
        Person actor = currentUserService.requireCurrentPerson();
        LaborComplaint entity = requireComplaint(id);

        if (request.registrationDate() != null) {
            entity.setRegistrationDate(request.registrationDate());
        }
        if (request.receiptDate() != null) {
            entity.setReceiptDate(request.receiptDate());
        }
        if (request.complaintYear() != null) {
            entity.setComplaintYear(request.complaintYear());
        }
        if (request.complaintNumber() != null) {
            String complaintNumber = requireText(request.complaintNumber(), "complaintNumber");
            ensureUniqueComplaintNumber(TenantContext.getRequired(), complaintNumber, entity.getId());
            entity.setComplaintNumber(complaintNumber);
            entity.setComplaintYear(resolveComplaintYear(complaintNumber, entity.getComplaintYear()));
        }
        if (request.subject() != null) {
            entity.setSubject(trimToNull(request.subject()));
        }
        if (request.summary() != null) {
            entity.setSummary(trimToNull(request.summary()));
        }
        if (request.legalRequest() != null) {
            entity.setLegalRequest(trimToNull(request.legalRequest()));
        }
        if (request.assignedLawyerId() != null) {
            entity.setAssignedLawyer(resolveLawyer(request.assignedLawyerId()));
        }
        if (request.clients() != null) {
            removePartiesByKind(entity, DomainEnums.LaborComplaintPartyKind.CLIENT);
            applyClientParties(entity, resolveClientInputs(request.clients(), null));
            syncLegacyPartyFields(entity);
        }
        if (request.opponents() != null) {
            removePartiesByKind(entity, DomainEnums.LaborComplaintPartyKind.OPPONENT);
            applyOpponentParties(entity, resolveOpponentInputs(request.opponents(), null, null));
            syncLegacyPartyFields(entity);
        } else if (request.opponentName() != null || request.opponentCapacity() != null) {
            removePartiesByKind(entity, DomainEnums.LaborComplaintPartyKind.OPPONENT);
            applyOpponentParties(entity, resolveOpponentInputs(
                    null, request.opponentName(), request.opponentCapacity()));
            syncLegacyPartyFields(entity);
        }
        if (request.linkedDisputeId() != null) {
            entity.setLinkedDispute(resolveDispute(request.linkedDisputeId()));
        }
        if (request.internalReference() != null) {
            entity.setInternalReference(trimToNull(request.internalReference()));
        }
        if (request.externalPartners() != null) {
            entity.setExternalPartners(trimToNull(request.externalPartners()));
        }
        if (request.opponentRepresentative() != null) {
            entity.setOpponentRepresentative(trimToNull(request.opponentRepresentative()));
        }
        if (request.cooperatingOffices() != null) {
            entity.setCooperatingOffices(trimToNull(request.cooperatingOffices()));
        }
        if (request.molSessions() != null) {
            entity.getMolSessions().clear();
            applyMolSessions(entity, request.molSessions());
        }

        LaborComplaint saved = repository.save(entity);
        auditLogService.recordSuccess(
                AuditAction.LABOR_COMPLAINT_UPDATED,
                actor,
                "LaborComplaint",
                saved.getId(),
                Map.of("complaintNumber", saved.getComplaintNumber()));
        return toResponse(saved);
    }

    public LaborComplaintResponse updateStatus(UUID id, LaborComplaintStatusUpdateRequest request) {
        Person actor = currentUserService.requireCurrentPerson();
        LaborComplaint entity = requireComplaint(id);
        DomainEnums.LaborComplaintStatus newStatus = request.status();

        switch (newStatus) {
            case ACTIVE -> {
                entity.setStatus(newStatus);
                clearSavedFields(entity);
                clearReferralFields(entity);
            }
            case SAVED -> {
                requireText(request.savedReason(), "savedReason");
                entity.setStatus(newStatus);
                entity.setSavedReason(trimToNull(request.savedReason()));
                entity.setSavedAt(LocalDateTime.now());
                clearReferralFields(entity);
            }
            case REFERRAL_TO_COMMITTEE -> {
                entity.setStatus(newStatus);
                entity.setLinkedCase(resolveCase(request.linkedCaseId()));
                entity.setReferralNotes(trimToNull(request.referralNotes()));
                entity.setReferredAt(LocalDateTime.now());
                clearSavedFields(entity);
            }
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported status");
        }

        LaborComplaint saved = repository.save(entity);
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("complaintNumber", saved.getComplaintNumber());
        meta.put("status", saved.getStatus().name());
        auditLogService.recordSuccess(
                AuditAction.LABOR_COMPLAINT_STATUS_CHANGED,
                actor,
                "LaborComplaint",
                saved.getId(),
                meta);
        return toResponse(saved);
    }

    LaborComplaint requireComplaint(UUID id) {
        return repository.findScopedById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "labor complaint not found"));
    }

    private Company currentTenantReference() {
        return companyRepository.getReferenceById(TenantContext.getRequired());
    }

    private Person requireClient(UUID clientId) {
        Person person = personRepository.findScopedById(clientId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "client not found"));
        if (person.getSystemRole() != DomainEnums.SystemRole.CLIENT) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "person is not a client");
        }
        return person;
    }

    private Person resolveLawyer(UUID lawyerId) {
        if (lawyerId == null) {
            return null;
        }
        Person person = personRepository.findScopedById(lawyerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "lawyer not found"));
        if (!LAWYER_ROLES.contains(person.getSystemRole())
                && person.getSystemRole() != DomainEnums.SystemRole.ADMIN) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "person is not eligible as assigned lawyer");
        }
        return person;
    }

    private Dispute resolveDispute(UUID disputeId) {
        if (disputeId == null) {
            return null;
        }
        return disputeRepository.findScopedById(disputeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "dispute not found"));
    }

    private CaseFile resolveCase(UUID caseId) {
        if (caseId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "linkedCaseId is required for referral");
        }
        return caseFileRepository.findScopedById(caseId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "case not found"));
    }

    private String resolveComplaintNumber(String requested, int year, UUID tenantId, UUID excludeId) {
        String custom = trimToNull(requested);
        if (custom != null) {
            ensureUniqueComplaintNumber(tenantId, custom, excludeId);
            return custom;
        }
        int seq = numberSequenceRepository.allocateNextSequence(tenantId, year);
        return String.format(Locale.ROOT, "LW-%d-%06d", year, seq);
    }

    private void ensureUniqueComplaintNumber(UUID tenantId, String complaintNumber, UUID excludeId) {
        boolean exists = excludeId == null
                ? repository.existsByCompany_IdAndComplaintNumber(tenantId, complaintNumber)
                : repository.existsByCompany_IdAndComplaintNumberAndIdNot(tenantId, complaintNumber, excludeId);
        if (exists) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "complaint number already exists");
        }
    }

    private static int resolveComplaintYear(String complaintNumber, int fallbackYear) {
        if (complaintNumber == null || !complaintNumber.startsWith("LW-")) {
            return fallbackYear;
        }
        String[] parts = complaintNumber.split("-");
        if (parts.length >= 2) {
            try {
                int parsed = Integer.parseInt(parts[1]);
                if (parsed >= 1900 && parsed <= 2100) {
                    return parsed;
                }
            } catch (NumberFormatException ignored) {
                // keep fallback
            }
        }
        return fallbackYear;
    }

    private void applyClientParties(LaborComplaint entity, List<LaborComplaintClientInput> clients) {
        Company company = entity.getCompany();
        int order = 0;
        for (LaborComplaintClientInput input : clients) {
            Person client = requireClient(input.clientId());
            LaborComplaintParty party = new LaborComplaintParty();
            party.setCompany(company);
            party.setLaborComplaint(entity);
            party.setPartyKind(DomainEnums.LaborComplaintPartyKind.CLIENT);
            party.setPerson(client);
            party.setCapacity(input.capacity());
            party.setSortOrder(order++);
            entity.getParties().add(party);
        }
    }

    private void applyOpponentParties(LaborComplaint entity, List<LaborComplaintOpponentInput> opponents) {
        if (opponents == null || opponents.isEmpty()) {
            return;
        }
        Company company = entity.getCompany();
        int order = 0;
        for (LaborComplaintOpponentInput input : opponents) {
            String name = requireText(input.name(), "opponent name");
            LaborComplaintParty party = new LaborComplaintParty();
            party.setCompany(company);
            party.setLaborComplaint(entity);
            party.setPartyKind(DomainEnums.LaborComplaintPartyKind.OPPONENT);
            party.setDisplayName(name);
            party.setCapacity(input.capacity());
            party.setSortOrder(order++);
            entity.getParties().add(party);
        }
    }

    private void removePartiesByKind(LaborComplaint entity, DomainEnums.LaborComplaintPartyKind kind) {
        entity.getParties().removeIf(p -> p.getPartyKind() == kind);
    }

    private void syncLegacyPartyFields(LaborComplaint entity) {
        LaborComplaintParty firstClient = firstParty(entity, DomainEnums.LaborComplaintPartyKind.CLIENT);
        if (firstClient == null || firstClient.getPerson() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "at least one client is required");
        }
        entity.setClient(firstClient.getPerson());

        LaborComplaintParty firstOpponent = firstParty(entity, DomainEnums.LaborComplaintPartyKind.OPPONENT);
        if (firstOpponent != null) {
            entity.setOpponentName(trimToNull(firstOpponent.getDisplayName()));
            entity.setOpponentCapacity(firstOpponent.getCapacity());
        } else {
            entity.setOpponentName(null);
            entity.setOpponentCapacity(null);
        }
    }

    private List<LaborComplaintClientInput> resolveClientInputs(
            List<LaborComplaintClientInput> clients,
            UUID legacyClientId) {
        if (clients != null && !clients.isEmpty()) {
            List<LaborComplaintClientInput> resolved = new ArrayList<>();
            for (LaborComplaintClientInput input : clients) {
                requireClient(input.clientId());
                if (resolved.stream().noneMatch(c -> c.clientId().equals(input.clientId()))) {
                    resolved.add(input);
                }
            }
            if (resolved.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "at least one client is required");
            }
            return resolved;
        }
        if (legacyClientId != null) {
            return List.of(new LaborComplaintClientInput(legacyClientId, null));
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "at least one client is required");
    }

    private List<LaborComplaintOpponentInput> resolveOpponentInputs(
            List<LaborComplaintOpponentInput> opponents,
            String legacyName,
            DomainEnums.LaborComplaintPartyCapacity legacyCapacity) {
        if (opponents != null && !opponents.isEmpty()) {
            return opponents.stream()
                    .filter(o -> !isBlank(o.name()))
                    .toList();
        }
        String name = trimToNull(legacyName);
        if (name == null) {
            return List.of();
        }
        return List.of(new LaborComplaintOpponentInput(name, legacyCapacity));
    }

    private LaborComplaintParty firstParty(LaborComplaint entity, DomainEnums.LaborComplaintPartyKind kind) {
        return entity.getParties().stream()
                .filter(p -> p.getPartyKind() == kind)
                .min(Comparator.comparingInt(LaborComplaintParty::getSortOrder))
                .orElse(null);
    }

    private List<LaborComplaintParty> sortedParties(LaborComplaint entity, DomainEnums.LaborComplaintPartyKind kind) {
        return entity.getParties().stream()
                .filter(p -> p.getPartyKind() == kind)
                .sorted(Comparator.comparingInt(LaborComplaintParty::getSortOrder))
                .toList();
    }

    private String joinPartyNames(List<LaborComplaintParty> parties, DomainEnums.LaborComplaintPartyKind kind) {
        return parties.stream()
                .filter(p -> p.getPartyKind() == kind)
                .sorted(Comparator.comparingInt(LaborComplaintParty::getSortOrder))
                .map(p -> kind == DomainEnums.LaborComplaintPartyKind.CLIENT
                        ? p.getPerson() != null ? p.getPerson().getFullName() : null
                        : p.getDisplayName())
                .filter(name -> name != null && !name.isBlank())
                .collect(Collectors.joining("، "));
    }

    private void applyMolSessions(LaborComplaint entity, List<LaborComplaintMolSessionInput> inputs) {
        if (inputs == null) {
            return;
        }
        Company company = entity.getCompany();
        for (LaborComplaintMolSessionInput input : inputs) {
            LaborComplaintMolSession session = new LaborComplaintMolSession();
            session.setCompany(company);
            session.setLaborComplaint(entity);
            session.setSessionDate(input.sessionDate());
            session.setLocation(trimToNull(input.location()));
            session.setNotes(trimToNull(input.notes()));
            entity.getMolSessions().add(session);
        }
    }

    private void clearSavedFields(LaborComplaint entity) {
        entity.setSavedReason(null);
        entity.setSavedAt(null);
    }

    private void clearReferralFields(LaborComplaint entity) {
        entity.setReferralNotes(null);
        entity.setReferredAt(null);
        entity.setLinkedCase(null);
    }

    private LaborComplaintListItemResponse toListItem(LaborComplaint c) {
        String clientNames = joinPartyNames(c.getParties(), DomainEnums.LaborComplaintPartyKind.CLIENT);
        if (isBlank(clientNames) && c.getClient() != null) {
            clientNames = c.getClient().getFullName();
        }
        String opponentNames = joinPartyNames(c.getParties(), DomainEnums.LaborComplaintPartyKind.OPPONENT);
        if (isBlank(opponentNames)) {
            opponentNames = c.getOpponentName();
        }
        return new LaborComplaintListItemResponse(
                c.getId(),
                c.getComplaintNumber(),
                c.getComplaintYear(),
                c.getRegistrationDate(),
                c.getReceiptDate(),
                c.getSubject(),
                c.getStatus(),
                c.getClient() != null ? c.getClient().getFullName() : null,
                c.getClient() != null ? c.getClient().getId() : null,
                isBlank(clientNames) ? null : clientNames,
                c.getAssignedLawyer() != null ? c.getAssignedLawyer().getFullName() : null,
                c.getOpponentName(),
                isBlank(opponentNames) ? null : opponentNames,
                c.getInternalReference(),
                c.getCreatedAt(),
                c.getUpdatedAt());
    }

    private LaborComplaintResponse toResponse(LaborComplaint c) {
        List<LaborComplaintMolSessionResponse> sessions = c.getMolSessions().stream()
                .map(s -> new LaborComplaintMolSessionResponse(
                        s.getId(), s.getSessionDate(), s.getLocation(), s.getNotes()))
                .toList();
        List<LaborComplaintClientResponse> clients = sortedParties(c, DomainEnums.LaborComplaintPartyKind.CLIENT).stream()
                .map(p -> new LaborComplaintClientResponse(p.getId(), toPartySummary(p.getPerson()), p.getCapacity()))
                .toList();
        List<LaborComplaintOpponentResponse> opponents = sortedParties(c, DomainEnums.LaborComplaintPartyKind.OPPONENT).stream()
                .map(p -> new LaborComplaintOpponentResponse(p.getId(), p.getDisplayName(), p.getCapacity()))
                .toList();
        LaborComplaintDisputeTeamSnapshot teamSnapshot = null;
        if (c.getLinkedDispute() != null) {
            List<DisputeTeamMemberResponse> members = disputeTeamService.list(c.getLinkedDispute().getId());
            teamSnapshot = new LaborComplaintDisputeTeamSnapshot(members);
        }
        return new LaborComplaintResponse(
                c.getId(),
                c.getComplaintNumber(),
                c.getComplaintYear(),
                c.getRegistrationDate(),
                c.getReceiptDate(),
                c.getSubject(),
                c.getSummary(),
                c.getLegalRequest(),
                c.getStatus(),
                toPartySummary(c.getClient()),
                clients,
                toPartySummary(c.getAssignedLawyer()),
                c.getOpponentName(),
                c.getOpponentCapacity(),
                opponents,
                c.getSavedReason(),
                c.getSavedAt(),
                c.getReferralNotes(),
                c.getReferredAt(),
                c.getLinkedCase() != null ? c.getLinkedCase().getId() : null,
                c.getLinkedCase() != null ? c.getLinkedCase().getCaseNumber() : null,
                c.getLinkedDispute() != null ? c.getLinkedDispute().getId() : null,
                c.getLinkedDispute() != null ? c.getLinkedDispute().getTitle() : null,
                c.getInternalReference(),
                c.getExternalPartners(),
                c.getOpponentRepresentative(),
                c.getCooperatingOffices(),
                teamSnapshot,
                sessions,
                toPartySummary(c.getCreatedBy()),
                c.getCreatedAt(),
                c.getUpdatedAt());
    }

    private static PartySummary toPartySummary(Person person) {
        if (person == null) {
            return null;
        }
        return new PartySummary(person.getId(), person.getFullName(), person.getSystemRole());
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static String requireText(String value, String field) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " is required");
        }
        return trimmed;
    }
}
