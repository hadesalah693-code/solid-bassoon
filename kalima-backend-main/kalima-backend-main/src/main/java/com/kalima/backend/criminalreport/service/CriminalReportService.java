package com.kalima.backend.criminalreport.service;

import com.kalima.backend.audit.AuditAction;
import com.kalima.backend.audit.service.AuditLogService;
import com.kalima.backend.auth.support.CurrentUserService;
import com.kalima.backend.casefile.dto.PartySummary;
import com.kalima.backend.casefile.entity.CaseFile;
import com.kalima.backend.casefile.repository.CaseFileRepository;
import com.kalima.backend.criminalreport.dto.CriminalReportClientInput;
import com.kalima.backend.criminalreport.dto.CriminalReportClientResponse;
import com.kalima.backend.criminalreport.dto.CriminalReportCreateRequest;
import com.kalima.backend.criminalreport.dto.CriminalReportInvestigationSessionInput;
import com.kalima.backend.criminalreport.dto.CriminalReportInvestigationSessionResponse;
import com.kalima.backend.criminalreport.dto.CriminalReportListItemResponse;
import com.kalima.backend.criminalreport.dto.CriminalReportLocationInput;
import com.kalima.backend.criminalreport.dto.CriminalReportLocationResponse;
import com.kalima.backend.criminalreport.dto.CriminalReportOpponentInput;
import com.kalima.backend.criminalreport.dto.CriminalReportOpponentResponse;
import com.kalima.backend.criminalreport.dto.CriminalReportResponse;
import com.kalima.backend.criminalreport.dto.CriminalReportStatusUpdateRequest;
import com.kalima.backend.criminalreport.dto.CriminalReportUpdateRequest;
import com.kalima.backend.criminalreport.entity.CriminalReport;
import com.kalima.backend.criminalreport.entity.CriminalReportInvestigationSession;
import com.kalima.backend.criminalreport.entity.CriminalReportParty;
import com.kalima.backend.criminalreport.entity.CriminalReportLocation;
import com.kalima.backend.criminalreport.repository.CriminalReportNumberSequenceRepository;
import com.kalima.backend.criminalreport.repository.CriminalReportRepository;
import com.kalima.backend.criminalreport.repository.CriminalReportSpecifications;
import com.kalima.backend.dispute.entity.Dispute;
import com.kalima.backend.dispute.repository.DisputeRepository;
import com.kalima.backend.domain.entity.Company;
import com.kalima.backend.domain.entity.DomainEnums;
import com.kalima.backend.domain.entity.Person;
import com.kalima.backend.domain.repository.CompanyRepository;
import com.kalima.backend.domain.repository.PersonRepository;
import com.kalima.backend.domain.tenant.TenantContext;
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
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.Comparator;
import java.util.stream.Collectors;
import java.util.UUID;

@Service
@Transactional
public class CriminalReportService {

    private static final Set<DomainEnums.SystemRole> LAWYER_ROLES = EnumSet.of(
            DomainEnums.SystemRole.LAWYER,
            DomainEnums.SystemRole.SENIOR_LAWYER);

    private final CriminalReportRepository repository;
    private final CriminalReportNumberSequenceRepository numberSequenceRepository;
    private final PersonRepository personRepository;
    private final CompanyRepository companyRepository;
    private final CaseFileRepository caseFileRepository;
    private final DisputeRepository disputeRepository;
    private final CurrentUserService currentUserService;
    private final AuditLogService auditLogService;

    public CriminalReportService(CriminalReportRepository repository,
                                 CriminalReportNumberSequenceRepository numberSequenceRepository,
                                 PersonRepository personRepository,
                                 CompanyRepository companyRepository,
                                 CaseFileRepository caseFileRepository,
                                 DisputeRepository disputeRepository,
                                 CurrentUserService currentUserService,
                                 AuditLogService auditLogService) {
        this.repository = repository;
        this.numberSequenceRepository = numberSequenceRepository;
        this.personRepository = personRepository;
        this.companyRepository = companyRepository;
        this.caseFileRepository = caseFileRepository;
        this.disputeRepository = disputeRepository;
        this.currentUserService = currentUserService;
        this.auditLogService = auditLogService;
    }

    public CriminalReportResponse create(CriminalReportCreateRequest request) {
        Person actor = currentUserService.requireCurrentPerson();
        List<CriminalReportClientInput> clientInputs = resolveClientInputs(request.clients(), request.clientId());
        List<CriminalReportOpponentInput> opponents = resolveOpponentInputs(
                request.opponents(), request.opponentName(), request.opponentCapacity());
        Person lawyer = resolveLawyer(request.assignedLawyerId());
        Dispute linkedDispute = resolveDispute(request.linkedDisputeId());

        int year = request.registrationDate().getYear();
        UUID tenantId = TenantContext.getRequired();
        String reportNumber = resolveReportNumber(request.reportNumber(), year, tenantId, null);
        year = resolveReportYear(reportNumber, year);

        LocalDate incidentDate = request.incidentDate() != null
                ? request.incidentDate()
                : LocalDate.now(ZoneId.systemDefault());

        CriminalReport entity = new CriminalReport();
        entity.setCompany(currentTenantReference());
        entity.setReportNumber(reportNumber);
        entity.setReportYear(year);
        entity.setRegistrationDate(request.registrationDate());
        entity.setIncidentDate(incidentDate);
        entity.setProsecutionOffice(trimToNull(request.prosecutionOffice()));
        entity.setSecurityCenter(trimToNull(request.securityCenter()));
        entity.setCharge(trimToNull(request.charge()));
        entity.setSubject(trimToNull(request.subject()));
        entity.setSummary(trimToNull(request.summary()));
        entity.setLegalRequest(trimToNull(request.legalRequest()));
        entity.setStatus(DomainEnums.CriminalReportStatus.UNDER_INVESTIGATION);
        entity.setAssignedLawyer(lawyer);
        entity.setLinkedDispute(linkedDispute);
        entity.setInternalSource(trimToNull(request.internalSource()));
        entity.setLegalConsultant(trimToNull(request.legalConsultant()));
        entity.setExternalConsultant(trimToNull(request.externalConsultant()));
        entity.setPartnerNotes(trimToNull(request.partnerNotes()));
        entity.setCreatedBy(actor);
        applyClientParties(entity, clientInputs);
        applyOpponentParties(entity, opponents);
        syncLegacyPartyFields(entity);
        applyLocations(entity, request.locations());
        applySessions(entity, request.investigationSessions());

        CriminalReport saved = repository.save(entity);
        auditLogService.recordSuccess(
                AuditAction.CRIMINAL_REPORT_CREATED,
                actor,
                "CriminalReport",
                saved.getId(),
                Map.of("reportNumber", saved.getReportNumber(), "clientId", clientInputs.get(0).clientId().toString()));
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public Page<CriminalReportListItemResponse> search(
            DomainEnums.CriminalReportStatus status,
            UUID clientId,
            UUID assignedLawyerId,
            String search,
            Pageable pageable) {
        String normalizedSearch = search == null || search.isBlank() ? null : search.trim();
        Page<CriminalReport> page = repository.findAll(
                CriminalReportSpecifications.filtered(status, clientId, assignedLawyerId, normalizedSearch),
                pageable);
        return page.map(this::toListItem);
    }

    @Transactional(readOnly = true)
    public CriminalReportResponse findById(UUID id) {
        return toResponse(requireReport(id));
    }

    public CriminalReportResponse update(UUID id, CriminalReportUpdateRequest request) {
        Person actor = currentUserService.requireCurrentPerson();
        CriminalReport entity = requireReport(id);
        ensureEditable(entity);

        if (request.registrationDate() != null) {
            entity.setRegistrationDate(request.registrationDate());
            entity.setReportYear(request.registrationDate().getYear());
        }
        if (request.reportNumber() != null) {
            String reportNumber = requireText(request.reportNumber(), "reportNumber");
            ensureUniqueReportNumber(TenantContext.getRequired(), reportNumber, entity.getId());
            entity.setReportNumber(reportNumber);
            entity.setReportYear(resolveReportYear(reportNumber, entity.getReportYear()));
        }
        if (request.incidentDate() != null || request.registrationDate() != null) {
            entity.setIncidentDate(request.incidentDate());
        }
        if (request.prosecutionOffice() != null) {
            entity.setProsecutionOffice(trimToNull(request.prosecutionOffice()));
        }
        if (request.securityCenter() != null) {
            entity.setSecurityCenter(trimToNull(request.securityCenter()));
        }
        if (request.charge() != null) {
            entity.setCharge(trimToNull(request.charge()));
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
            removePartiesByKind(entity, DomainEnums.CriminalReportPartyKind.CLIENT);
            applyClientParties(entity, resolveClientInputs(request.clients(), null));
            syncLegacyPartyFields(entity);
        }
        if (request.opponents() != null) {
            removePartiesByKind(entity, DomainEnums.CriminalReportPartyKind.OPPONENT);
            applyOpponentParties(entity, resolveOpponentInputs(request.opponents(), null, null));
            syncLegacyPartyFields(entity);
        } else if (request.opponentName() != null || request.opponentCapacity() != null) {
            removePartiesByKind(entity, DomainEnums.CriminalReportPartyKind.OPPONENT);
            applyOpponentParties(entity, resolveOpponentInputs(
                    null, request.opponentName(), request.opponentCapacity()));
            syncLegacyPartyFields(entity);
        }
        if (request.linkedDisputeId() != null) {
            entity.setLinkedDispute(resolveDispute(request.linkedDisputeId()));
        }
        if (request.internalSource() != null) {
            entity.setInternalSource(trimToNull(request.internalSource()));
        }
        if (request.legalConsultant() != null) {
            entity.setLegalConsultant(trimToNull(request.legalConsultant()));
        }
        if (request.externalConsultant() != null) {
            entity.setExternalConsultant(trimToNull(request.externalConsultant()));
        }
        if (request.partnerNotes() != null) {
            entity.setPartnerNotes(trimToNull(request.partnerNotes()));
        }
        if (request.locations() != null) {
            entity.getLocations().clear();
            applyLocations(entity, request.locations());
        }
        if (request.investigationSessions() != null) {
            entity.getInvestigationSessions().clear();
            applySessions(entity, request.investigationSessions());
        }

        CriminalReport saved = repository.save(entity);
        auditLogService.recordSuccess(
                AuditAction.CRIMINAL_REPORT_UPDATED,
                actor,
                "CriminalReport",
                saved.getId(),
                Map.of("reportNumber", saved.getReportNumber()));
        return toResponse(saved);
    }

    public CriminalReportResponse updateStatus(UUID id, CriminalReportStatusUpdateRequest request) {
        Person actor = currentUserService.requireCurrentPerson();
        CriminalReport entity = requireReport(id);
        DomainEnums.CriminalReportStatus newStatus = request.status();

        switch (newStatus) {
            case UNDER_INVESTIGATION -> {
                entity.setStatus(newStatus);
                clearArchiveFields(entity);
            }
            case REFERRAL -> {
                entity.setStatus(newStatus);
                entity.setLinkedCase(resolveCase(request.linkedCaseId()));
            }
            case ARCHIVED -> {
                requireText(request.archiveReason(), "archiveReason");
                requirePositive(request.grievancePeriodDays(), "grievancePeriodDays");
                entity.setStatus(newStatus);
                entity.setArchiveReason(trimToNull(request.archiveReason()));
                entity.setGrievancePeriodDays(request.grievancePeriodDays());
                entity.setGrievanceDeadline(resolveGrievanceDeadline(
                        request.grievanceDeadline(),
                        request.grievancePeriodDays()));
                entity.setArchivedAt(LocalDateTime.now());
            }
            case FINAL_ARCHIVED -> {
                requireText(request.finalArchiveReason(), "finalArchiveReason");
                entity.setStatus(newStatus);
                entity.setFinalArchiveReason(trimToNull(request.finalArchiveReason()));
                entity.setDecisionIssuer(trimToNull(request.decisionIssuer()));
                entity.setDecisionNumber(trimToNull(request.decisionNumber()));
                entity.setDecisionReceivedDate(request.decisionReceivedDate());
                entity.setFinalDecisionBasis(trimToNull(request.finalDecisionBasis()));
                entity.setAppealPeriodDays(request.appealPeriodDays());
                entity.setFinalDecisionLevel(trimToNull(request.finalDecisionLevel()));
                entity.setFinalArchivedAt(LocalDateTime.now());
            }
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported status");
        }

        CriminalReport saved = repository.save(entity);
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("reportNumber", saved.getReportNumber());
        meta.put("status", saved.getStatus().name());
        auditLogService.recordSuccess(
                AuditAction.CRIMINAL_REPORT_STATUS_CHANGED,
                actor,
                "CriminalReport",
                saved.getId(),
                meta);
        return toResponse(saved);
    }

    private CriminalReport requireReport(UUID id) {
        return repository.findScopedById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "criminal report not found"));
    }

    private void ensureEditable(CriminalReport entity) {
        if (entity.getStatus() == DomainEnums.CriminalReportStatus.FINAL_ARCHIVED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "final archived reports cannot be edited");
        }
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

    private String resolveReportNumber(String requested, int year, UUID tenantId, UUID excludeId) {
        String custom = trimToNull(requested);
        if (custom != null) {
            ensureUniqueReportNumber(tenantId, custom, excludeId);
            return custom;
        }
        int seq = numberSequenceRepository.allocateNextSequence(tenantId, year);
        return String.format(Locale.ROOT, "%04d/%d", seq, year);
    }

    private void ensureUniqueReportNumber(UUID tenantId, String reportNumber, UUID excludeId) {
        boolean exists = excludeId == null
                ? repository.existsByCompany_IdAndReportNumber(tenantId, reportNumber)
                : repository.existsByCompany_IdAndReportNumberAndIdNot(tenantId, reportNumber, excludeId);
        if (exists) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "report number already exists");
        }
    }

    private static int resolveReportYear(String reportNumber, int fallbackYear) {
        if (reportNumber == null || !reportNumber.contains("/")) {
            return fallbackYear;
        }
        String suffix = reportNumber.substring(reportNumber.lastIndexOf('/') + 1).trim();
        try {
            int parsed = Integer.parseInt(suffix);
            if (parsed >= 1900 && parsed <= 2100) {
                return parsed;
            }
        } catch (NumberFormatException ignored) {
            // keep fallback
        }
        return fallbackYear;
    }

    private void applyClientParties(CriminalReport entity, List<CriminalReportClientInput> clients) {
        Company company = entity.getCompany();
        int order = 0;
        for (CriminalReportClientInput input : clients) {
            Person client = requireClient(input.clientId());
            CriminalReportParty party = new CriminalReportParty();
            party.setCompany(company);
            party.setCriminalReport(entity);
            party.setPartyKind(DomainEnums.CriminalReportPartyKind.CLIENT);
            party.setPerson(client);
            party.setCapacity(input.capacity());
            party.setSortOrder(order++);
            entity.getParties().add(party);
        }
    }

    private void applyOpponentParties(CriminalReport entity, List<CriminalReportOpponentInput> opponents) {
        if (opponents == null || opponents.isEmpty()) {
            return;
        }
        Company company = entity.getCompany();
        int order = 0;
        for (CriminalReportOpponentInput input : opponents) {
            String name = requireText(input.name(), "opponent name");
            CriminalReportParty party = new CriminalReportParty();
            party.setCompany(company);
            party.setCriminalReport(entity);
            party.setPartyKind(DomainEnums.CriminalReportPartyKind.OPPONENT);
            party.setDisplayName(name);
            party.setCapacity(input.capacity());
            party.setSortOrder(order++);
            entity.getParties().add(party);
        }
    }

    private void removePartiesByKind(CriminalReport entity, DomainEnums.CriminalReportPartyKind kind) {
        entity.getParties().removeIf(p -> p.getPartyKind() == kind);
    }

    private void syncLegacyPartyFields(CriminalReport entity) {
        CriminalReportParty firstClient = firstParty(entity, DomainEnums.CriminalReportPartyKind.CLIENT);
        if (firstClient == null || firstClient.getPerson() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "at least one client is required");
        }
        entity.setClient(firstClient.getPerson());

        CriminalReportParty firstOpponent = firstParty(entity, DomainEnums.CriminalReportPartyKind.OPPONENT);
        if (firstOpponent != null) {
            entity.setOpponentName(trimToNull(firstOpponent.getDisplayName()));
            entity.setOpponentCapacity(firstOpponent.getCapacity());
        } else {
            entity.setOpponentName(null);
            entity.setOpponentCapacity(null);
        }
    }

    private List<CriminalReportClientInput> resolveClientInputs(
            List<CriminalReportClientInput> clients,
            UUID legacyClientId) {
        if (clients != null && !clients.isEmpty()) {
            List<CriminalReportClientInput> resolved = new ArrayList<>();
            for (CriminalReportClientInput input : clients) {
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
            return List.of(new CriminalReportClientInput(legacyClientId, null));
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "at least one client is required");
    }

    private List<CriminalReportOpponentInput> resolveOpponentInputs(
            List<CriminalReportOpponentInput> opponents,
            String legacyName,
            DomainEnums.CriminalReportPartyCapacity legacyCapacity) {
        if (opponents != null && !opponents.isEmpty()) {
            return opponents.stream()
                    .filter(o -> !isBlank(o.name()))
                    .toList();
        }
        String name = trimToNull(legacyName);
        if (name == null) {
            return List.of();
        }
        return List.of(new CriminalReportOpponentInput(name, legacyCapacity));
    }

    private CriminalReportParty firstParty(CriminalReport entity, DomainEnums.CriminalReportPartyKind kind) {
        return entity.getParties().stream()
                .filter(p -> p.getPartyKind() == kind)
                .min(Comparator.comparingInt(CriminalReportParty::getSortOrder))
                .orElse(null);
    }

    private List<CriminalReportParty> sortedParties(CriminalReport entity, DomainEnums.CriminalReportPartyKind kind) {
        return entity.getParties().stream()
                .filter(p -> p.getPartyKind() == kind)
                .sorted(Comparator.comparingInt(CriminalReportParty::getSortOrder))
                .toList();
    }

    private String joinPartyNames(List<CriminalReportParty> parties, DomainEnums.CriminalReportPartyKind kind) {
        return parties.stream()
                .filter(p -> p.getPartyKind() == kind)
                .sorted(Comparator.comparingInt(CriminalReportParty::getSortOrder))
                .map(p -> kind == DomainEnums.CriminalReportPartyKind.CLIENT
                        ? p.getPerson() != null ? p.getPerson().getFullName() : null
                        : p.getDisplayName())
                .filter(name -> name != null && !name.isBlank())
                .collect(Collectors.joining("، "));
    }

    private void applyLocations(CriminalReport entity, List<CriminalReportLocationInput> inputs) {
        if (inputs == null) {
            return;
        }
        Company company = entity.getCompany();
        for (CriminalReportLocationInput input : inputs) {
            if (isBlank(input.locationType()) && isBlank(input.locationDescription())) {
                continue;
            }
            CriminalReportLocation loc = new CriminalReportLocation();
            loc.setCompany(company);
            loc.setCriminalReport(entity);
            loc.setLocationType(trimToNull(input.locationType()));
            loc.setLocationDescription(trimToNull(input.locationDescription()));
            entity.getLocations().add(loc);
        }
    }

    private void applySessions(CriminalReport entity, List<CriminalReportInvestigationSessionInput> inputs) {
        if (inputs == null) {
            return;
        }
        Company company = entity.getCompany();
        for (CriminalReportInvestigationSessionInput input : inputs) {
            CriminalReportInvestigationSession session = new CriminalReportInvestigationSession();
            session.setCompany(company);
            session.setCriminalReport(entity);
            session.setSessionDate(input.sessionDate());
            session.setLocation(trimToNull(input.location()));
            session.setNotes(trimToNull(input.notes()));
            entity.getInvestigationSessions().add(session);
        }
    }

    private LocalDate resolveGrievanceDeadline(LocalDate explicit, Integer periodDays) {
        if (explicit != null) {
            return explicit;
        }
        if (periodDays == null || periodDays <= 0) {
            return null;
        }
        return LocalDate.now(ZoneId.systemDefault()).plusDays(periodDays);
    }

    private void clearArchiveFields(CriminalReport entity) {
        entity.setArchiveReason(null);
        entity.setGrievancePeriodDays(null);
        entity.setGrievanceDeadline(null);
        entity.setArchivedAt(null);
    }

    private CriminalReportListItemResponse toListItem(CriminalReport r) {
        String clientNames = joinPartyNames(r.getParties(), DomainEnums.CriminalReportPartyKind.CLIENT);
        if (clientNames.isBlank() && r.getClient() != null) {
            clientNames = r.getClient().getFullName();
        }
        String opponentNames = joinPartyNames(r.getParties(), DomainEnums.CriminalReportPartyKind.OPPONENT);
        if (opponentNames.isBlank()) {
            opponentNames = r.getOpponentName();
        }
        return new CriminalReportListItemResponse(
                r.getId(),
                r.getReportNumber(),
                r.getReportYear(),
                r.getRegistrationDate(),
                r.getProsecutionOffice(),
                r.getSecurityCenter(),
                r.getCharge(),
                r.getStatus(),
                r.getClient() != null ? r.getClient().getFullName() : null,
                r.getClient() != null ? r.getClient().getId() : null,
                clientNames.isBlank() ? null : clientNames,
                r.getAssignedLawyer() != null ? r.getAssignedLawyer().getFullName() : null,
                r.getOpponentName(),
                opponentNames.isBlank() ? null : opponentNames,
                r.getCreatedAt(),
                r.getUpdatedAt());
    }

    private CriminalReportResponse toResponse(CriminalReport r) {
        List<CriminalReportLocationResponse> locations = r.getLocations().stream()
                .map(l -> new CriminalReportLocationResponse(l.getId(), l.getLocationType(), l.getLocationDescription()))
                .toList();
        List<CriminalReportInvestigationSessionResponse> sessions = r.getInvestigationSessions().stream()
                .map(s -> new CriminalReportInvestigationSessionResponse(
                        s.getId(), s.getSessionDate(), s.getLocation(), s.getNotes()))
                .toList();
        List<CriminalReportClientResponse> clients = sortedParties(r, DomainEnums.CriminalReportPartyKind.CLIENT).stream()
                .map(p -> new CriminalReportClientResponse(p.getId(), toPartySummary(p.getPerson()), p.getCapacity()))
                .toList();
        List<CriminalReportOpponentResponse> opponents = sortedParties(r, DomainEnums.CriminalReportPartyKind.OPPONENT).stream()
                .map(p -> new CriminalReportOpponentResponse(p.getId(), p.getDisplayName(), p.getCapacity()))
                .toList();
        return new CriminalReportResponse(
                r.getId(),
                r.getReportNumber(),
                r.getReportYear(),
                r.getRegistrationDate(),
                r.getIncidentDate(),
                r.getProsecutionOffice(),
                r.getSecurityCenter(),
                r.getCharge(),
                r.getSubject(),
                r.getSummary(),
                r.getLegalRequest(),
                r.getStatus(),
                toPartySummary(r.getClient()),
                clients,
                toPartySummary(r.getAssignedLawyer()),
                r.getOpponentName(),
                r.getOpponentCapacity(),
                opponents,
                r.getArchiveReason(),
                r.getGrievancePeriodDays(),
                r.getGrievanceDeadline(),
                r.getArchivedAt(),
                r.getFinalArchiveReason(),
                r.getDecisionIssuer(),
                r.getDecisionNumber(),
                r.getDecisionReceivedDate(),
                r.getFinalDecisionBasis(),
                r.getAppealPeriodDays(),
                r.getFinalDecisionLevel(),
                r.getFinalArchivedAt(),
                r.getLinkedCase() != null ? r.getLinkedCase().getId() : null,
                r.getLinkedCase() != null ? r.getLinkedCase().getCaseNumber() : null,
                r.getLinkedDispute() != null ? r.getLinkedDispute().getId() : null,
                r.getLinkedDispute() != null ? r.getLinkedDispute().getTitle() : null,
                r.getInternalSource(),
                r.getLegalConsultant(),
                r.getExternalConsultant(),
                r.getPartnerNotes(),
                locations,
                sessions,
                toPartySummary(r.getCreatedBy()),
                r.getCreatedAt(),
                r.getUpdatedAt());
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

    private static void requirePositive(Integer value, String field) {
        if (value == null || value <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " must be positive");
        }
    }
}
