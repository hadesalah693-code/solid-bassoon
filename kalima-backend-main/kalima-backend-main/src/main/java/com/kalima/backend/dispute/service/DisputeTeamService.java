package com.kalima.backend.dispute.service;

import com.kalima.backend.auth.support.CurrentUserService;
import com.kalima.backend.casefile.dto.PartySummary;
import com.kalima.backend.dispute.dto.DisputeTeamMemberAssignment;
import com.kalima.backend.dispute.dto.DisputeTeamMemberResponse;
import com.kalima.backend.dispute.dto.UpdateDisputeTeamRequest;
import com.kalima.backend.dispute.entity.Dispute;
import com.kalima.backend.dispute.entity.DisputeTeamMember;
import com.kalima.backend.dispute.repository.DisputeRepository;
import com.kalima.backend.dispute.repository.DisputeTeamMemberRepository;
import com.kalima.backend.domain.entity.Company;
import com.kalima.backend.domain.entity.DomainEnums;
import com.kalima.backend.domain.entity.DomainEnums.DisputeTeamRole;
import com.kalima.backend.domain.entity.DomainEnums.SystemRole;
import com.kalima.backend.domain.entity.Person;
import com.kalima.backend.domain.repository.PersonRepository;
import com.kalima.backend.notification.service.NotificationService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
@Transactional
public class DisputeTeamService {

    private static final Set<SystemRole> LAWYER_ROLES = EnumSet.of(
            SystemRole.LAWYER,
            SystemRole.SENIOR_LAWYER);

    private static final Set<SystemRole> ADMIN_ROLES = EnumSet.of(
            SystemRole.SECRETARY,
            SystemRole.ADMIN);

    private static final Set<SystemRole> STAFF_ROLES = EnumSet.of(
            SystemRole.ADMIN,
            SystemRole.SECRETARY,
            SystemRole.LAWYER,
            SystemRole.SENIOR_LAWYER,
            SystemRole.ACCOUNTANT);

    private static final Map<DisputeTeamRole, Set<SystemRole>> ROLE_MATRIX = Map.of(
            DisputeTeamRole.ASSIGNED_LAWYER, LAWYER_ROLES,
            DisputeTeamRole.LEGAL_CONSULTANT, LAWYER_ROLES,
            DisputeTeamRole.ADMINISTRATIVE_OFFICER, ADMIN_ROLES,
            DisputeTeamRole.COORDINATION_REVIEW, ADMIN_ROLES,
            DisputeTeamRole.TRANSLATION_OFFICER, STAFF_ROLES,
            DisputeTeamRole.INITIAL_REVIEW, LAWYER_ROLES,
            DisputeTeamRole.FINAL_REVIEW, EnumSet.of(SystemRole.SENIOR_LAWYER));

    private final DisputeTeamMemberRepository teamMemberRepository;
    private final DisputeRepository disputeRepository;
    private final PersonRepository personRepository;
    private final CurrentUserService currentUserService;
    private final NotificationService notificationService;

    public DisputeTeamService(DisputeTeamMemberRepository teamMemberRepository,
                              DisputeRepository disputeRepository,
                              PersonRepository personRepository,
                              CurrentUserService currentUserService,
                              NotificationService notificationService) {
        this.teamMemberRepository = teamMemberRepository;
        this.disputeRepository = disputeRepository;
        this.personRepository = personRepository;
        this.currentUserService = currentUserService;
        this.notificationService = notificationService;
    }

    @Transactional(readOnly = true)
    public List<DisputeTeamMemberResponse> list(UUID disputeId) {
        loadDispute(disputeId);
        return teamMemberRepository.findByDispute_IdOrderByRoleAsc(disputeId).stream()
                .map(this::toResponse)
                .toList();
    }

    public List<DisputeTeamMemberResponse> updateTeam(UUID disputeId, UpdateDisputeTeamRequest request) {
        Dispute dispute = loadDispute(disputeId);
        ensureMutable(dispute);
        Person actor = currentUserService.requireCurrentPerson();

        Person previousLawyer = dispute.getAssignedLawyer();
        if (previousLawyer == null) {
            previousLawyer = teamMemberRepository
                    .findByDispute_IdAndRole(disputeId, DisputeTeamRole.ASSIGNED_LAWYER)
                    .map(DisputeTeamMember::getPerson)
                    .orElse(null);
        }

        for (DisputeTeamMemberAssignment assignment : request.assignments()) {
            applyAssignment(dispute, assignment.role(), assignment.personId());
        }

        syncAssignedLawyerShortcut(dispute);

        Dispute saved = disputeRepository.save(dispute);

        Person newLawyer = saved.getAssignedLawyer();
        UUID prevId = previousLawyer == null ? null : previousLawyer.getId();
        UUID nextId = newLawyer == null ? null : newLawyer.getId();
        if (!Objects.equals(prevId, nextId)) {
            Person adminOfficer = teamMemberRepository
                    .findByDispute_IdAndRole(disputeId, DisputeTeamRole.ADMINISTRATIVE_OFFICER)
                    .map(DisputeTeamMember::getPerson)
                    .orElse(null);
            notificationService.notifyDisputeLawyerChanged(
                    saved, previousLawyer, newLawyer, adminOfficer, actor);
        }

        return teamMemberRepository.findByDispute_IdOrderByRoleAsc(disputeId).stream()
                .map(this::toResponse)
                .toList();
    }

    public void setMemberOnCreate(Dispute dispute, DisputeTeamRole role, UUID personId) {
        if (personId == null) {
            return;
        }
        applyAssignment(dispute, role, personId);
        if (role == DisputeTeamRole.ASSIGNED_LAWYER) {
            syncAssignedLawyerShortcut(dispute);
        }
    }

    public void clearMember(Dispute dispute, DisputeTeamRole role) {
        teamMemberRepository.deleteByDispute_IdAndRole(dispute.getId(), role);
        if (role == DisputeTeamRole.ASSIGNED_LAWYER) {
            dispute.setAssignedLawyer(null);
        }
    }

    @Transactional(readOnly = true)
    public Person findMemberPerson(UUID disputeId, DisputeTeamRole role) {
        return teamMemberRepository.findByDispute_IdAndRole(disputeId, role)
                .map(DisputeTeamMember::getPerson)
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public List<DisputeTeamMemberResponse> listForDisputeDetail(UUID disputeId) {
        return teamMemberRepository.findByDispute_IdOrderByRoleAsc(disputeId).stream()
                .map(this::toResponse)
                .toList();
    }

    private void applyAssignment(Dispute dispute, DisputeTeamRole role, UUID personId) {
        if (personId == null) {
            teamMemberRepository.deleteByDispute_IdAndRole(dispute.getId(), role);
            return;
        }

        Person person = loadPerson(personId);
        validateSystemRoleAllowed(role, person);

        DisputeTeamMember member = teamMemberRepository
                .findByDispute_IdAndRole(dispute.getId(), role)
                .orElseGet(() -> {
                    DisputeTeamMember m = new DisputeTeamMember();
                    m.setCompany(dispute.getCompany());
                    m.setDispute(dispute);
                    m.setRole(role);
                    return m;
                });
        member.setPerson(person);
        teamMemberRepository.save(member);
    }

    private void syncAssignedLawyerShortcut(Dispute dispute) {
        Person lawyer = teamMemberRepository
                .findByDispute_IdAndRole(dispute.getId(), DisputeTeamRole.ASSIGNED_LAWYER)
                .map(DisputeTeamMember::getPerson)
                .orElse(null);
        dispute.setAssignedLawyer(lawyer);
    }

    private void validateSystemRoleAllowed(DisputeTeamRole role, Person person) {
        Set<SystemRole> allowed = ROLE_MATRIX.get(role);
        if (allowed != null && !allowed.contains(person.getSystemRole())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Person role " + person.getSystemRole()
                            + " is not allowed for dispute team role " + role);
        }
        if (!person.isActive()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Person is inactive: " + person.getId());
        }
    }

    private Dispute loadDispute(UUID disputeId) {
        return disputeRepository.findScopedById(disputeId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Dispute not found: " + disputeId));
    }

    private Person loadPerson(UUID personId) {
        return personRepository.findScopedById(personId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Person not found in this company: " + personId));
    }

    private static void ensureMutable(Dispute d) {
        if (d.getConvertedCase() != null
                || d.getStatus() == DomainEnums.DisputeStatus.CONVERTED_TO_CASE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Cannot modify team on a converted dispute");
        }
    }

    private DisputeTeamMemberResponse toResponse(DisputeTeamMember m) {
        Person p = m.getPerson();
        PartySummary person = new PartySummary(p.getId(), displayName(p), p.getSystemRole());
        return new DisputeTeamMemberResponse(m.getId(), m.getRole(), person);
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
}
