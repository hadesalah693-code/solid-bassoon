package com.kalima.backend.dispute.service;

import com.kalima.backend.casefile.dto.PartySummary;
import com.kalima.backend.dispute.dto.CreateDisputePartyRequest;
import com.kalima.backend.dispute.dto.DisputePartyResponse;
import com.kalima.backend.dispute.dto.UpdateDisputePartyRequest;
import com.kalima.backend.dispute.entity.Dispute;
import com.kalima.backend.dispute.entity.DisputeParty;
import com.kalima.backend.dispute.repository.DisputePartyRepository;
import com.kalima.backend.dispute.repository.DisputeRepository;
import com.kalima.backend.domain.entity.DomainEnums;
import com.kalima.backend.domain.entity.DomainEnums.DisputePartyRole;
import com.kalima.backend.domain.entity.DomainEnums.SystemRole;
import com.kalima.backend.domain.entity.Person;
import com.kalima.backend.domain.repository.PersonRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

/**
 * Additional dispute roster beyond {@link Dispute#getClient()} and textual {@link Dispute#getOpponentName()}.
 */
@Service
@Transactional
public class DisputePartyService {

    private final DisputePartyRepository disputePartyRepository;
    private final DisputeRepository disputeRepository;
    private final PersonRepository personRepository;

    public DisputePartyService(
            DisputePartyRepository disputePartyRepository,
            DisputeRepository disputeRepository,
            PersonRepository personRepository) {
        this.disputePartyRepository = disputePartyRepository;
        this.disputeRepository = disputeRepository;
        this.personRepository = personRepository;
    }

    public List<DisputePartyResponse> list(UUID disputeId) {
        loadDispute(disputeId);
        return disputePartyRepository.findByDispute_IdOrderBySortOrderAscCreatedAtAsc(disputeId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public DisputePartyResponse create(UUID disputeId, CreateDisputePartyRequest req) {
        Dispute dispute = loadDispute(disputeId);
        ensureMutable(dispute);
        DisputePartyRole role = req.role();
        UUID personId = req.personId();
        String displayName = trimToNull(req.displayName());
        if (personId == null && displayName == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Provide personId or a non-blank displayName");
        }

        Person person = personId == null ? null : loadTenantPerson(personId);
        validateCoClient(dispute, role, person);

        if (person != null
                && disputePartyRepository.existsByDispute_IdAndPerson_IdAndRole(
                        disputeId, person.getId(), role)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Duplicate party for this dispute, person, and role");
        }

        DisputeParty e = new DisputeParty();
        e.setCompany(dispute.getCompany());
        e.setDispute(dispute);
        e.setRole(role);
        e.setPerson(person);
        if (person == null) {
            e.setDisplayName(requireText(displayName, "displayName"));
        } else {
            e.setDisplayName(displayName);
        }
        e.setPhone(trimToNull(req.phone()));
        e.setNotes(trimToNull(req.notes()));
        e.setSortOrder(req.sortOrder() != null ? req.sortOrder() : 0);
        disputePartyRepository.save(e);
        return toResponse(e);
    }

    public DisputePartyResponse update(UUID disputeId, UUID partyId, UpdateDisputePartyRequest req) {
        Dispute dispute = loadDispute(disputeId);
        ensureMutable(dispute);
        DisputeParty e = loadParty(disputeId, partyId);

        DisputePartyRole role = req.role() != null ? req.role() : e.getRole();

        /* null personId means "leave linkage unchanged" — never clear personLink via PATCH omission. */
        Person person = e.getPerson();
        if (req.personId() != null) {
            person = loadTenantPerson(req.personId());
        }

        String displayName = e.getDisplayName();
        if (req.displayName() != null) {
            displayName = trimToNull(req.displayName());
        }

        if (person == null) {
            displayName = requireText(displayName, "displayName");
        }

        validateCoClient(dispute, role, person);

        if (person != null && disputePartyRepository.existsByDispute_IdAndPerson_IdAndRoleAndIdNot(
                        disputeId, person.getId(), role, partyId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Duplicate party for this dispute, person, and role");
        }

        e.setRole(role);
        e.setPerson(person);
        e.setDisplayName(trimToNull(displayName));
        if (req.phone() != null) {
            e.setPhone(trimToNull(req.phone()));
        }
        if (req.notes() != null) {
            e.setNotes(trimToNull(req.notes()));
        }
        if (req.sortOrder() != null) {
            e.setSortOrder(req.sortOrder());
        }
        disputePartyRepository.save(e);
        return toResponse(e);
    }

    public void delete(UUID disputeId, UUID partyId) {
        Dispute dispute = loadDispute(disputeId);
        ensureMutable(dispute);
        DisputeParty e = loadParty(disputeId, partyId);
        disputePartyRepository.delete(e);
    }

    private DisputeParty loadParty(UUID disputeId, UUID partyId) {
        DisputeParty e = disputePartyRepository.findScopedById(partyId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Party not found: " + partyId));
        if (!e.getDispute().getId().equals(disputeId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Party not found: " + partyId);
        }
        return e;
    }

    private Dispute loadDispute(UUID id) {
        return disputeRepository.findScopedById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Dispute not found: " + id));
    }

    private void ensureMutable(Dispute d) {
        if (d.getConvertedCase() != null || d.getStatus() == DomainEnums.DisputeStatus.CONVERTED_TO_CASE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Cannot modify parties on a dispute that has been converted to a case");
        }
    }

    private void validateCoClient(Dispute dispute, DisputePartyRole role, Person person) {
        if (role != DisputePartyRole.CO_CLIENT) {
            return;
        }
        if (person == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "CO_CLIENT requires personId referencing a CLIENT in this company");
        }
        if (person.getSystemRole() != SystemRole.CLIENT) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "CO_CLIENT must reference a person with role CLIENT");
        }
        Person primary = dispute.getClient();
        if (primary != null && primary.getId().equals(person.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Party duplicates the dispute primary client");
        }
        if (!person.isActive()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Referenced client is inactive");
        }
    }

    private Person loadTenantPerson(UUID id) {
        Person p = personRepository.findScopedById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "person not found in this company: " + id));
        if (!p.isActive()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Person is inactive: " + id);
        }
        return p;
    }

    private DisputePartyResponse toResponse(DisputeParty e) {
        return new DisputePartyResponse(
                e.getId(),
                e.getDispute().getId(),
                e.getRole(),
                toPartySummary(e.getPerson()),
                e.getDisplayName(),
                e.getPhone(),
                e.getNotes(),
                e.getSortOrder());
    }

    private static PartySummary toPartySummary(Person person) {
        if (person == null) {
            return null;
        }
        return new PartySummary(person.getId(), partyDisplay(person), person.getSystemRole());
    }

    private static String partyDisplay(Person person) {
        String n = person.getFullName();
        if (n != null && !n.isBlank()) {
            return n.trim();
        }
        if (person.getCompanyName() != null && !person.getCompanyName().isBlank()) {
            return person.getCompanyName().trim();
        }
        return person.getEmail() != null ? person.getEmail() : person.getId().toString();
    }

    private static String trimToNull(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        return s.trim();
    }

    private static String requireText(String value, String field) {
        String t = trimToNull(value);
        if (t == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " is required");
        }
        return t;
    }
}
