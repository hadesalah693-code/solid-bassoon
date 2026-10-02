package com.kalima.backend.delegation.settings.service;

import com.kalima.backend.audit.AuditAction;
import com.kalima.backend.audit.service.AuditLogService;
import com.kalima.backend.auth.support.CurrentUserService;
import com.kalima.backend.casefile.dto.PartySummary;
import com.kalima.backend.delegation.DelegationAgentRoles;
import com.kalima.backend.delegation.settings.dto.DelegationAttorneyMatchResponse;
import com.kalima.backend.delegation.settings.dto.DelegationOfficeAttorneyCreateRequest;
import com.kalima.backend.delegation.settings.dto.DelegationOfficeAttorneyResponse;
import com.kalima.backend.delegation.settings.dto.DelegationOfficeAttorneyUpdateRequest;
import com.kalima.backend.delegation.settings.entity.DelegationOfficeAttorney;
import com.kalima.backend.delegation.settings.repository.DelegationOfficeAttorneyRepository;
import com.kalima.backend.domain.entity.Company;
import com.kalima.backend.domain.entity.Person;
import com.kalima.backend.domain.repository.CompanyRepository;
import com.kalima.backend.domain.repository.PersonRepository;
import com.kalima.backend.domain.tenant.TenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * CRUD + lookup service for Delegation Settings' "office attorneys" —
 * the staff members a tenant pre-registers as the usual agents on its
 * powers of attorney.
 *
 * <p>Business rules enforced here (in addition to the DB-level partial
 * unique index on {@code is_default}):
 * <ul>
 *   <li>Only persons whose role is in {@link DelegationAgentRoles#AGENT_ROLES} may be registered.</li>
 *   <li>A person may only be registered once per tenant.</li>
 *   <li>At most one attorney per tenant may be marked default; marking a new
 *       one clears the previous default.</li>
 *   <li>An inactive attorney can never remain the default.</li>
 * </ul>
 */
@Service
@Transactional
public class DelegationOfficeAttorneyService {

    /** Arabic combining diacritics (tashkeel + tatweel) that OCR frequently renders inconsistently. */
    private static final Pattern ARABIC_DIACRITICS = Pattern.compile("[\\u064B-\\u0652\\u0670\\u0640]");

    private final DelegationOfficeAttorneyRepository repository;
    private final PersonRepository personRepository;
    private final CompanyRepository companyRepository;
    private final CurrentUserService currentUserService;
    private final AuditLogService auditLogService;

    public DelegationOfficeAttorneyService(DelegationOfficeAttorneyRepository repository,
                                           PersonRepository personRepository,
                                           CompanyRepository companyRepository,
                                           CurrentUserService currentUserService,
                                           AuditLogService auditLogService) {
        this.repository = repository;
        this.personRepository = personRepository;
        this.companyRepository = companyRepository;
        this.currentUserService = currentUserService;
        this.auditLogService = auditLogService;
    }

    public DelegationOfficeAttorneyResponse create(DelegationOfficeAttorneyCreateRequest request) {
        Person actor = currentUserService.requireCurrentPerson();
        Person person = personRepository.findScopedById(request.personId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "person not found in this company: " + request.personId()));
        if (!DelegationAgentRoles.AGENT_ROLES.contains(person.getSystemRole())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Referenced person is not eligible to be an office attorney: " + request.personId());
        }
        if (repository.existsScopedByPersonId(request.personId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "person is already registered as an office attorney: " + request.personId());
        }

        boolean active = request.active() == null || request.active();
        boolean wantsDefault = Boolean.TRUE.equals(request.isDefault());
        if (wantsDefault && !active) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "an inactive attorney cannot be marked as default");
        }

        DelegationOfficeAttorney entity = new DelegationOfficeAttorney();
        entity.setCompany(currentTenantReference());
        entity.setPerson(person);
        entity.setActive(active);
        entity.setDefault(false);

        if (wantsDefault) {
            clearExistingDefault(actor, null);
            entity.setDefault(true);
        }

        DelegationOfficeAttorney saved = repository.save(entity);
        auditLogService.recordSuccess(
                AuditAction.DELEGATION_ATTORNEY_CREATED,
                actor,
                "DelegationOfficeAttorney",
                saved.getId(),
                Map.of("personId", person.getId().toString()));
        return toResponse(saved);
    }

    public DelegationOfficeAttorneyResponse update(UUID id, DelegationOfficeAttorneyUpdateRequest request) {
        Person actor = currentUserService.requireCurrentPerson();
        DelegationOfficeAttorney entity = loadOrThrow(id);

        if (request.active() != null) {
            entity.setActive(request.active());
            if (!request.active()) {
                // An inactive attorney cannot remain the default.
                entity.setDefault(false);
            }
        }

        if (request.isDefault() != null) {
            if (request.isDefault()) {
                if (!entity.isActive()) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "an inactive attorney cannot be marked as default");
                }
                clearExistingDefault(actor, id);
                entity.setDefault(true);
            } else {
                entity.setDefault(false);
            }
        }

        DelegationOfficeAttorney saved = repository.save(entity);
        auditLogService.recordSuccess(
                AuditAction.DELEGATION_ATTORNEY_UPDATED,
                actor,
                "DelegationOfficeAttorney",
                saved.getId(),
                Map.of("personId", saved.getPerson().getId().toString()));
        return toResponse(saved);
    }

    public void delete(UUID id) {
        Person actor = currentUserService.requireCurrentPerson();
        DelegationOfficeAttorney entity = loadOrThrow(id);
        repository.delete(entity);
        auditLogService.recordSuccess(
                AuditAction.DELEGATION_ATTORNEY_DELETED,
                actor,
                "DelegationOfficeAttorney",
                id,
                Map.of("personId", entity.getPerson().getId().toString()));
    }

    @Transactional(readOnly = true)
    public List<DelegationOfficeAttorneyResponse> list(boolean activeOnly) {
        List<DelegationOfficeAttorney> rows = activeOnly
                ? repository.findActiveOrdered()
                : repository.findAllOrdered();
        return rows.stream().map(this::toResponse).toList();
    }

    /**
     * Resolves what the delegation-creation form should do with the agent
     * field, per the product rule: exactly one configured (active) attorney
     * auto-fills; otherwise the user must pick from the list via
     * {@link #list(boolean)} (default-flagged one pre-selected, if any).
     */
    @Transactional(readOnly = true)
    public PartySummary resolveAutoAgent() {
        List<DelegationOfficeAttorney> active = repository.findActiveOrdered();
        if (active.size() == 1) {
            return toParty(active.get(0).getPerson());
        }
        return null;
    }

    /**
     * Matches a free-text name (e.g. extracted via OCR from a scanned POA)
     * against the tenant's active office attorneys. Groundwork for the OCR
     * intake flow — actual OCR-to-delegation wiring lands in a later phase.
     */
    @Transactional(readOnly = true)
    public DelegationAttorneyMatchResponse matchByName(String extractedName) {
        String needle = normalize(extractedName);
        if (needle.isEmpty()) {
            return DelegationAttorneyMatchResponse.none();
        }

        DelegationOfficeAttorney exact = null;
        DelegationOfficeAttorney partial = null;
        for (DelegationOfficeAttorney attorney : repository.findActiveOrdered()) {
            String candidate = normalize(attorney.getPerson().getFullName());
            if (candidate.isEmpty()) {
                continue;
            }
            if (candidate.equals(needle)) {
                exact = attorney;
                break;
            }
            if (partial == null && (candidate.contains(needle) || needle.contains(candidate))) {
                partial = attorney;
            }
        }

        if (exact != null) {
            return new DelegationAttorneyMatchResponse(
                    true, toParty(exact.getPerson()), DelegationAttorneyMatchResponse.MatchType.EXACT);
        }
        if (partial != null) {
            return new DelegationAttorneyMatchResponse(
                    true, toParty(partial.getPerson()), DelegationAttorneyMatchResponse.MatchType.PARTIAL);
        }
        return DelegationAttorneyMatchResponse.none();
    }

    /**
     * Unsets any existing default(s) and flushes immediately, recording an
     * audit entry for each attorney that lost its default status as a
     * side-effect. The explicit flush matters: the partial unique index on
     * {@code is_default} is enforced per-statement (Postgres doesn't support
     * deferring a partial unique index), so the "clear old default" UPDATE
     * must physically land before the caller's "set new default" UPDATE is
     * issued — otherwise Hibernate's flush ordering (driven by
     * persistence-context load order, not call order) can emit them in the
     * wrong sequence and trip the constraint even though the net result
     * would have been valid.
     */
    private void clearExistingDefault(Person actor, UUID excludeId) {
        for (DelegationOfficeAttorney existing : repository.findAllOrdered()) {
            if (existing.isDefault() && !existing.getId().equals(excludeId)) {
                existing.setDefault(false);
                repository.saveAndFlush(existing);
                auditLogService.recordSuccess(
                        AuditAction.DELEGATION_ATTORNEY_UPDATED,
                        actor,
                        "DelegationOfficeAttorney",
                        existing.getId(),
                        Map.of(
                                "personId", existing.getPerson().getId().toString(),
                                "isDefault", "false",
                                "reason", "superseded by another attorney being set as default"));
            }
        }
    }

    private DelegationOfficeAttorney loadOrThrow(UUID id) {
        return repository.findScopedById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "office attorney not found: " + id));
    }

    private Company currentTenantReference() {
        return companyRepository.getReferenceById(TenantContext.getRequired());
    }

    private DelegationOfficeAttorneyResponse toResponse(DelegationOfficeAttorney a) {
        return new DelegationOfficeAttorneyResponse(
                a.getId(),
                toParty(a.getPerson()),
                a.isActive(),
                a.isDefault(),
                a.getCreatedAt(),
                a.getUpdatedAt());
    }

    private PartySummary toParty(Person person) {
        String name = person.getFullName() != null && !person.getFullName().isBlank()
                ? person.getFullName().trim()
                : person.getEmail();
        return new PartySummary(person.getId(), name, person.getSystemRole());
    }

    private static String normalize(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim().replaceAll("\\s+", " ");

        // Strip diacritics and normalize Alef variants (أ/إ/آ -> ا) *before* the
        // honorific check below, since OCR/typed text commonly renders honorifics
        // fully vocalized (e.g. "السَّيِّد") or with a different Alef form.
        trimmed = ARABIC_DIACRITICS.matcher(trimmed).replaceAll("");
        trimmed = trimmed.replace('\u0623', '\u0627')
                .replace('\u0625', '\u0627')
                .replace('\u0622', '\u0627');
        trimmed = trimmed.trim().replaceAll("\\s+", " ");

        // Alef variants already collapsed above, so "الأستاذ" and "الاستاذ" are
        // now the same string — only the plain-Alef form needs listing.
        for (String honorific : new String[] {"السيد/", "السيد", "الاستاذ/", "الاستاذ"}) {
            if (trimmed.startsWith(honorific)) {
                trimmed = trimmed.substring(honorific.length()).trim();
            }
        }
        return trimmed.toUpperCase(Locale.ROOT);
    }
}
