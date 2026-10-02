package com.kalima.backend.delegation.service;

import com.kalima.backend.audit.AuditAction;
import com.kalima.backend.audit.service.AuditLogService;
import com.kalima.backend.notification.service.NotificationService;
import com.kalima.backend.auth.support.CurrentUserService;
import com.kalima.backend.casefile.dto.PartySummary;
import com.kalima.backend.domain.entity.Company;
import com.kalima.backend.domain.entity.DomainEnums;
import com.kalima.backend.domain.entity.Person;
import com.kalima.backend.domain.repository.CompanyRepository;
import com.kalima.backend.domain.repository.PersonRepository;
import com.kalima.backend.domain.tenant.TenantContext;
import com.kalima.backend.delegation.DelegationAgentRoles;
import com.kalima.backend.delegation.dto.DelegationCreateRequest;
import com.kalima.backend.delegation.dto.DelegationListItemResponse;
import com.kalima.backend.delegation.dto.DelegationResponse;
import com.kalima.backend.delegation.dto.DelegationUpdateRequest;
import com.kalima.backend.delegation.entity.Delegation;
import com.kalima.backend.delegation.repository.DelegationSpecifications;
import com.kalima.backend.delegation.repository.DelegationNumberSequenceRepository;
import com.kalima.backend.delegation.repository.DelegationRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@Transactional
public class DelegationService {

    private static final Set<DomainEnums.SystemRole> AGENT_ROLES = DelegationAgentRoles.AGENT_ROLES;

    private final DelegationRepository delegationRepository;
    private final DelegationNumberSequenceRepository numberSequenceRepository;
    private final PersonRepository personRepository;
    private final CompanyRepository companyRepository;
    private final CurrentUserService currentUserService;
    private final AuditLogService auditLogService;
    private final DelegationCalendarSyncService calendarSyncService;
    private final DelegationMentionService mentionService;
    private final NotificationService notificationService;

    private static final int EXPORT_MAX_ROWS = 5000;

    public DelegationService(DelegationRepository delegationRepository,
                             DelegationNumberSequenceRepository numberSequenceRepository,
                             PersonRepository personRepository,
                             CompanyRepository companyRepository,
                             CurrentUserService currentUserService,
                             AuditLogService auditLogService,
                             DelegationCalendarSyncService calendarSyncService,
                             DelegationMentionService mentionService,
                             NotificationService notificationService) {
        this.delegationRepository = delegationRepository;
        this.numberSequenceRepository = numberSequenceRepository;
        this.personRepository = personRepository;
        this.companyRepository = companyRepository;
        this.currentUserService = currentUserService;
        this.auditLogService = auditLogService;
        this.calendarSyncService = calendarSyncService;
        this.mentionService = mentionService;
        this.notificationService = notificationService;
    }

    public DelegationResponse create(DelegationCreateRequest request) {
        Person actor = currentUserService.requireCurrentPerson();
        Person client = requireClient(request.clientId());
        Set<UUID> agentIdSet = resolveAgentIds(request.agentIds(), request.agentId());
        validateDates(request.issuedAt(), request.expiresAt());

        UUID tenantId = TenantContext.getRequired();
        int year = LocalDate.now(ZoneId.systemDefault()).getYear();
        int seq = numberSequenceRepository.allocateNextSequence(tenantId, year);
        String delegationNumber = "POA-" + year + "-" + String.format(Locale.ROOT, "%05d", seq);

        Delegation d = new Delegation();
        d.setCompany(currentTenantReference());
        d.setDelegationNumber(delegationNumber);
        d.setNotaryNumber(trimToNull(request.notaryNumber()));
        d.setClient(client);
        applyAgents(d, agentIdSet);
        d.setDelegationType(request.delegationType());
        d.setIssuingCountry(normalizeCountry(request.issuingCountry()));
        d.setIssuedAt(request.issuedAt());
        d.setExpiresAt(request.expiresAt());
        d.setNotes(trimToNull(request.notes()));
        d.setTitle(buildTitle(client, d.getNotaryNumber(), delegationNumber));
        d.setStatus(deriveStatus(null, request.expiresAt()));
        d.setPartnerIds(resolvePartnerIds(request.partnerIds()));
        applyReminderConfig(d, request.reminderOffsets(), request.reminderRecipientIds());

        Delegation saved = delegationRepository.save(d);
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("delegationNumber", saved.getDelegationNumber());
        meta.put("clientId", client.getId().toString());
        auditLogService.recordSuccess(
                AuditAction.DELEGATION_CREATED,
                actor,
                "Delegation",
                saved.getId(),
                meta);
        calendarSyncService.sync(saved, actor);
        mentionService.notifyAfterCreate(saved, actor);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public Page<DelegationListItemResponse> search(
            DomainEnums.DelegationStatus status,
            DomainEnums.DelegationType type,
            UUID clientId,
            UUID agentId,
            String issuingCountry,
            Boolean starredOnly,
            String search,
            Pageable pageable) {
        String normalizedSearch = search == null || search.isBlank()
                ? null
                : search.trim().toLowerCase(Locale.ROOT);
        String normalizedCountry = issuingCountry == null || issuingCountry.isBlank()
                ? null
                : issuingCountry.trim().toUpperCase(Locale.ROOT);
        Pageable paged = PageRequest.of(
                pageable.getPageNumber(),
                pageable.getPageSize(),
                Sort.by(Sort.Order.desc("starred"), Sort.Order.desc("createdAt")));
        LocalDate today = LocalDate.now(ZoneId.systemDefault());
        Page<Delegation> page = delegationRepository.findAll(
                DelegationSpecifications.filtered(
                        status,
                        today,
                        type,
                        clientId,
                        agentId,
                        normalizedCountry,
                        starredOnly,
                        normalizedSearch),
                paged);
        return page.map(this::toListItem);
    }

    @Transactional(readOnly = true)
    public byte[] exportCsv(
            DomainEnums.DelegationStatus status,
            DomainEnums.DelegationType type,
            UUID clientId,
            UUID agentId,
            String issuingCountry,
            Boolean starredOnly,
            String search) {
        Page<DelegationListItemResponse> page = search(
                status, type, clientId, agentId, issuingCountry, starredOnly, search,
                PageRequest.of(0, EXPORT_MAX_ROWS));
        StringBuilder sb = new StringBuilder();
        sb.append('\uFEFF');
        sb.append("رقم التوكيل,رقم التوثيق,العنوان,الموكل,الوكيل,النوع,بلد الإصدار,الحالة,تاريخ الإصدار,تاريخ الانتهاء,أيام متبقية,مميز\n");
        for (DelegationListItemResponse row : page.getContent()) {
            sb.append(csv(row.delegationNumber())).append(',');
            sb.append(csv(row.notaryNumber())).append(',');
            sb.append(csv(row.title())).append(',');
            sb.append(csv(partyName(row.client()))).append(',');
            sb.append(csv(formatAgentNames(row.agents(), row.agent()))).append(',');
            sb.append(csv(row.delegationType().name())).append(',');
            sb.append(csv(row.issuingCountry())).append(',');
            sb.append(csv(row.status().name())).append(',');
            sb.append(csv(String.valueOf(row.issuedAt()))).append(',');
            sb.append(csv(row.expiresAt() == null ? "" : row.expiresAt().toString())).append(',');
            sb.append(csv(row.daysRemaining() == null ? "" : row.daysRemaining().toString())).append(',');
            sb.append(csv(row.starred() ? "نعم" : "لا")).append('\n');
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    @Transactional(readOnly = true)
    public DelegationResponse getById(UUID id) {
        return toResponse(loadDelegation(id));
    }

    public DelegationResponse update(UUID id, DelegationUpdateRequest request) {
        Delegation d = loadDelegation(id);
        Person actor = currentUserService.requireCurrentPerson();
        String previousNotes = d.getNotes();
        DomainEnums.DelegationStatus previousStatus = d.getStatus();
        Set<UUID> previousRecipientIds = new HashSet<>(d.getReminderRecipientIds());

        if (request.agentIds() != null || request.agentId() != null) {
            applyAgents(d, resolveAgentIds(request.agentIds(), request.agentId()));
        }
        if (request.delegationType() != null) {
            d.setDelegationType(request.delegationType());
        }
        if (request.issuingCountry() != null && !request.issuingCountry().isBlank()) {
            d.setIssuingCountry(normalizeCountry(request.issuingCountry()));
        }
        if (request.issuedAt() != null) {
            d.setIssuedAt(request.issuedAt());
        }
        if (request.expiresAt() != null) {
            d.setExpiresAt(request.expiresAt());
        }
        validateDates(d.getIssuedAt(), d.getExpiresAt());

        if (request.notaryNumber() != null) {
            d.setNotaryNumber(trimToNull(request.notaryNumber()));
            d.setTitle(buildTitle(d.getClient(), d.getNotaryNumber(), d.getDelegationNumber()));
        }
        if (request.partnerIds() != null) {
            d.setPartnerIds(resolvePartnerIds(request.partnerIds()));
        }
        if (request.notes() != null) {
            d.setNotes(trimToNull(request.notes()));
        }

        DomainEnums.DelegationStatus nextStatus = request.status();
        if (nextStatus != null) {
            if (nextStatus == DomainEnums.DelegationStatus.CANCELLED) {
                if (request.cancelReason() == null) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "cancelReason is required when status is CANCELLED");
                }
                d.setCancelReason(request.cancelReason());
                d.setCancelReasonNote(trimToNull(request.cancelReasonNote()));
                d.setCancelledAt(request.cancelledAt());
                d.setCancellationAnnouncementAt(request.cancellationAnnouncementAt());
                d.setStatus(DomainEnums.DelegationStatus.CANCELLED);
            } else {
                d.setCancelReason(null);
                d.setCancelReasonNote(null);
                d.setCancelledAt(null);
                d.setCancellationAnnouncementAt(null);
                d.setStatus(deriveStatus(null, d.getExpiresAt()));
            }
        } else if (d.getStatus() == DomainEnums.DelegationStatus.CANCELLED) {
            if (request.cancelReasonNote() != null) {
                d.setCancelReasonNote(trimToNull(request.cancelReasonNote()));
            }
            if (request.cancelledAt() != null) {
                d.setCancelledAt(request.cancelledAt());
            }
            if (request.cancellationAnnouncementAt() != null) {
                d.setCancellationAnnouncementAt(request.cancellationAnnouncementAt());
            }
        } else {
            d.setStatus(deriveStatus(null, d.getExpiresAt()));
        }

        if (request.reminderOffsets() != null || request.reminderRecipientIds() != null) {
            applyReminderConfig(
                    d,
                    request.reminderOffsets() != null ? request.reminderOffsets() : List.copyOf(d.getReminderOffsets()),
                    request.reminderRecipientIds() != null
                            ? request.reminderRecipientIds()
                            : List.copyOf(d.getReminderRecipientIds()));
        }
        if (request.starred() != null) {
            d.setStarred(request.starred());
        }

        Delegation saved = delegationRepository.save(d);
        String auditAction = previousStatus != DomainEnums.DelegationStatus.CANCELLED
            && saved.getStatus() == DomainEnums.DelegationStatus.CANCELLED
            ? AuditAction.DELEGATION_CANCELLED
            : previousStatus == DomainEnums.DelegationStatus.CANCELLED
                && saved.getStatus() != DomainEnums.DelegationStatus.CANCELLED
                ? AuditAction.DELEGATION_REINSTATED
                : AuditAction.DELEGATION_UPDATED;
        auditLogService.recordSuccess(
            auditAction,
                actor,
                "Delegation",
                saved.getId(),
                Map.of("delegationNumber", saved.getDelegationNumber()));
        calendarSyncService.sync(saved, actor);
        if (previousStatus != DomainEnums.DelegationStatus.CANCELLED
                && saved.getStatus() == DomainEnums.DelegationStatus.CANCELLED) {
            notificationService.notifyDelegationCancelled(saved, actor);
        }
        mentionService.notifyAfterUpdate(saved, actor, previousNotes, previousRecipientIds);
        return toResponse(saved);
    }

    private Delegation loadDelegation(UUID id) {
        return delegationRepository.findScopedById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "delegation not found"));
    }

    private Company currentTenantReference() {
        UUID tenantId = TenantContext.getRequired();
        return companyRepository.getReferenceById(tenantId);
    }

    private Person requireClient(UUID clientId) {
        Person client = personRepository.findScopedById(clientId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "client not found in this company: " + clientId));
        if (client.getSystemRole() != DomainEnums.SystemRole.CLIENT) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Referenced person is not a CLIENT: " + clientId);
        }
        return client;
    }

    private Set<UUID> resolveAgentIds(List<UUID> agentIds, UUID legacyAgentId) {
        Set<UUID> resolved = new LinkedHashSet<>();
        if (agentIds != null) {
            for (UUID personId : agentIds) {
                if (personId == null) {
                    continue;
                }
                resolveAgent(personId);
                resolved.add(personId);
            }
        }
        if (resolved.isEmpty() && legacyAgentId != null) {
            resolveAgent(legacyAgentId);
            resolved.add(legacyAgentId);
        }
        return resolved;
    }

    private void applyAgents(Delegation delegation, Set<UUID> agentIds) {
        delegation.setAgentIds(new HashSet<>(agentIds));
        delegation.setAgent(agentIds.isEmpty() ? null : resolveAgent(agentIds.iterator().next()));
    }

    private Person resolveAgent(UUID agentId) {
        if (agentId == null) {
            return null;
        }
        Person agent = personRepository.findScopedById(agentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "agent not found in this company: " + agentId));
        if (!AGENT_ROLES.contains(agent.getSystemRole())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Referenced person cannot act as delegation agent: " + agentId);
        }
        return agent;
    }

    private Set<UUID> resolvePartnerIds(List<UUID> partnerIds) {
        Set<UUID> resolved = new HashSet<>();
        if (partnerIds == null) {
            return resolved;
        }
        for (UUID personId : partnerIds) {
            if (personId == null) {
                continue;
            }
            Person partner = personRepository.findScopedById(personId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "partner not found in this company: " + personId));
            if (partner.getSystemRole() != DomainEnums.SystemRole.CLIENT) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Referenced person is not a CLIENT: " + personId);
            }
            resolved.add(personId);
        }
        return resolved;
    }

    private void applyReminderConfig(Delegation d, List<Integer> offsets, List<UUID> recipientIds) {
        Set<Integer> normalizedOffsets = new HashSet<>();
        if (offsets != null) {
            for (Integer offset : offsets) {
                if (offset == null || offset <= 0) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "reminderOffsets must contain positive integers");
                }
                normalizedOffsets.add(offset);
            }
        }
        d.setReminderOffsets(normalizedOffsets);

        Set<UUID> normalizedRecipients = new HashSet<>();
        if (recipientIds != null) {
            for (UUID personId : recipientIds) {
                if (personId == null) {
                    continue;
                }
                Person p = personRepository.findScopedById(personId)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                "reminder recipient not found: " + personId));
                if (!AGENT_ROLES.contains(p.getSystemRole())) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "reminder recipient must be staff: " + personId);
                }
                normalizedRecipients.add(personId);
            }
        }
        d.setReminderRecipientIds(normalizedRecipients);
    }

    private static void validateDates(LocalDate issuedAt, LocalDate expiresAt) {
        if (issuedAt == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "issuedAt is required");
        }
        if (expiresAt != null && expiresAt.isBefore(issuedAt)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "expiresAt must be on or after issuedAt");
        }
    }

    private static String normalizeCountry(String country) {
        return country.trim().toUpperCase(Locale.ROOT);
    }

    private static String buildTitle(Person client, String notaryNumber, String delegationNumber) {
        String suffix = notaryNumber != null && !notaryNumber.isBlank()
                ? notaryNumber.trim()
                : delegationNumber;
        return displayName(client) + " / " + suffix;
    }

    private static DomainEnums.DelegationStatus deriveStatus(
            DomainEnums.DelegationStatus requested,
            LocalDate expiresAt) {
        if (requested == DomainEnums.DelegationStatus.CANCELLED) {
            return DomainEnums.DelegationStatus.CANCELLED;
        }
        if (expiresAt == null) {
            return DomainEnums.DelegationStatus.ACTIVE;
        }
        LocalDate today = LocalDate.now(ZoneId.systemDefault());
        if (expiresAt.isBefore(today)) {
            return DomainEnums.DelegationStatus.EXPIRED;
        }
        return DomainEnums.DelegationStatus.ACTIVE;
    }

    private DelegationListItemResponse toListItem(Delegation d) {
        List<PartySummary> agents = toParties(d.getAgentIds());
        return new DelegationListItemResponse(
                d.getId(),
                d.getDelegationNumber(),
                d.getNotaryNumber(),
                d.getTitle(),
                d.getDelegationType(),
                effectiveStatus(d),
                d.getIssuingCountry(),
                d.getIssuedAt(),
                d.getExpiresAt(),
                daysRemaining(d),
                d.isStarred(),
                toParty(d.getClient()),
                agents,
                primaryAgent(agents, d.getAgent()),
                d.getCreatedAt());
    }

    private DelegationResponse toResponse(Delegation d) {
        List<Integer> offsets = d.getReminderOffsets().stream().sorted().toList();
        List<UUID> recipients = d.getReminderRecipientIds().stream()
                .sorted(Comparator.naturalOrder())
                .toList();
        List<PartySummary> agents = toParties(d.getAgentIds());
        return new DelegationResponse(
                d.getId(),
                d.getDelegationNumber(),
                d.getNotaryNumber(),
                d.getTitle(),
                d.getDelegationType(),
                effectiveStatus(d),
                d.getIssuingCountry(),
                d.getCancelReason(),
                d.getCancelReasonNote(),
                d.getCancelledAt(),
                d.getCancellationAnnouncementAt(),
                d.getIssuedAt(),
                d.getExpiresAt(),
                daysRemaining(d),
                d.getNotes(),
                offsets,
                recipients,
                d.isStarred(),
                toParty(d.getClient()),
                toParties(d.getPartnerIds()),
                agents,
                primaryAgent(agents, d.getAgent()),
                d.getCreatedAt(),
                d.getUpdatedAt());
    }

    private static PartySummary primaryAgent(List<PartySummary> agents, Person fallback) {
        if (!agents.isEmpty()) {
            return agents.get(0);
        }
        if (fallback == null) {
            return null;
        }
        return new PartySummary(fallback.getId(), displayName(fallback), fallback.getSystemRole());
    }

    private static String formatAgentNames(List<PartySummary> agents, PartySummary primary) {
        if (agents != null && !agents.isEmpty()) {
            return agents.stream()
                    .map(DelegationService::partyName)
                    .filter(name -> name != null && !name.isBlank())
                    .collect(java.util.stream.Collectors.joining("، "));
        }
        return partyName(primary);
    }

    private List<PartySummary> toParties(Set<UUID> personIds) {
        if (personIds == null || personIds.isEmpty()) {
            return List.of();
        }
        return personRepository.findScopedByIdIn(personIds).stream()
                .map(this::toParty)
                .sorted(Comparator.comparing(PartySummary::fullName, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    private static String csv(String value) {
        if (value == null) {
            return "";
        }
        String escaped = value.replace("\"", "\"\"");
        if (escaped.contains(",") || escaped.contains("\"") || escaped.contains("\n")) {
            return "\"" + escaped + "\"";
        }
        return escaped;
    }

    private static String partyName(PartySummary party) {
        return party == null || party.fullName() == null ? "" : party.fullName();
    }

    private static DomainEnums.DelegationStatus effectiveStatus(Delegation d) {
        if (d.getStatus() == DomainEnums.DelegationStatus.CANCELLED) {
            return DomainEnums.DelegationStatus.CANCELLED;
        }
        return deriveStatus(null, d.getExpiresAt());
    }

    private static Long daysRemaining(Delegation d) {
        if (d.getStatus() == DomainEnums.DelegationStatus.CANCELLED || d.getExpiresAt() == null) {
            return null;
        }
        LocalDate today = LocalDate.now(ZoneId.systemDefault());
        if (d.getExpiresAt().isBefore(today)) {
            return ChronoUnit.DAYS.between(d.getExpiresAt(), today) * -1;
        }
        return ChronoUnit.DAYS.between(today, d.getExpiresAt());
    }

    private PartySummary toParty(Person person) {
        if (person == null) {
            return null;
        }
        return new PartySummary(person.getId(), displayName(person), person.getSystemRole());
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
}
