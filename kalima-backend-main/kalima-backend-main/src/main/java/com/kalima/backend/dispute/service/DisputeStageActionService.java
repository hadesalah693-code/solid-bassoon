package com.kalima.backend.dispute.service;

import com.kalima.backend.auth.support.CurrentUserService;
import com.kalima.backend.casefile.dto.PartySummary;
import com.kalima.backend.dispute.dto.CompleteDisputeStageActionRequest;
import com.kalima.backend.dispute.dto.CreateDisputeStageActionRequest;
import com.kalima.backend.dispute.dto.DisputeActionSummaryResponse;
import com.kalima.backend.dispute.dto.DisputeStageActionResponse;
import com.kalima.backend.dispute.dto.UpdateDisputeStageActionRequest;
import com.kalima.backend.dispute.entity.Dispute;
import com.kalima.backend.dispute.entity.DisputeAdministrativeUpdate;
import com.kalima.backend.dispute.entity.DisputeStage;
import com.kalima.backend.dispute.entity.DisputeStageAction;
import com.kalima.backend.dispute.repository.DisputeAdministrativeUpdateRepository;
import com.kalima.backend.dispute.repository.DisputeRepository;
import com.kalima.backend.dispute.repository.DisputeStageActionRepository;
import com.kalima.backend.dispute.repository.DisputeStageRepository;
import com.kalima.backend.domain.entity.DomainEnums;
import com.kalima.backend.domain.entity.Person;
import com.kalima.backend.domain.repository.PersonRepository;
import com.kalima.backend.domain.tenant.TenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class DisputeStageActionService {

    private final DisputeRepository disputeRepository;
    private final DisputeStageRepository disputeStageRepository;
    private final DisputeStageActionRepository actionRepository;
    private final DisputeAdministrativeUpdateRepository administrativeUpdateRepository;
    private final PersonRepository personRepository;
    private final CurrentUserService currentUserService;

    public DisputeStageActionService(
            DisputeRepository disputeRepository,
            DisputeStageRepository disputeStageRepository,
            DisputeStageActionRepository actionRepository,
            DisputeAdministrativeUpdateRepository administrativeUpdateRepository,
            PersonRepository personRepository,
            CurrentUserService currentUserService) {
        this.disputeRepository = disputeRepository;
        this.disputeStageRepository = disputeStageRepository;
        this.actionRepository = actionRepository;
        this.administrativeUpdateRepository = administrativeUpdateRepository;
        this.personRepository = personRepository;
        this.currentUserService = currentUserService;
    }

    @Transactional(readOnly = true)
    public DisputeActionSummaryResponse getSummary(UUID disputeId) {
        loadDispute(disputeId);
        LocalDateTime now = LocalDateTime.now();

        long openTotal = 0, overdue = 0, done = 0, cancelled = 0;
        for (Object[] row : actionRepository.countByStatusBucket(
                disputeId, TenantContext.getRequired(), now)) {
            String bucket = (String) row[0];
            long count = ((Number) row[1]).longValue();
            switch (bucket) {
                case "OPEN_TOTAL" -> openTotal = count;
                case "OVERDUE"    -> overdue   = count;
                case "DONE"       -> done      = count;
                case "CANCELLED"  -> cancelled = count;
            }
        }
        return new DisputeActionSummaryResponse(openTotal + overdue, overdue, done, cancelled);
    }

    @Transactional(readOnly = true)
    public List<DisputeStageActionResponse> listActions(UUID disputeId, UUID stageIdFilter) {
        loadDispute(disputeId);
        return actionRepository.findForDisputeOrdered(disputeId, stageIdFilter).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<DisputeStageActionResponse> listActionsForStage(UUID disputeId, UUID stageId) {
        loadDispute(disputeId);
        disputeStageRepository
                .findByIdAndDispute_Id(stageId, disputeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Stage not found for this dispute: " + stageId));
        return actionRepository.findForStageOrdered(stageId).stream()
                .map(this::toResponse)
                .toList();
    }

    public DisputeStageActionResponse addAction(UUID disputeId, CreateDisputeStageActionRequest req) {
        Dispute d = loadDispute(disputeId);
        Person actor = currentUserService.requireCurrentPerson();
        DisputeStage stage = resolveStage(disputeId, req.stageId());

        DisputeStageAction a = new DisputeStageAction();
        a.setCompany(d.getCompany());
        a.setDispute(d);
        a.setStage(stage);
        a.setActionType(req.actionType());
        a.setTitle(requireText(req.title(), "title"));
        a.setDescription(trimToNull(req.description()));
        a.setPriority(req.priority() != null ? req.priority() : DomainEnums.DisputeActionPriority.MEDIUM);
        a.setStatus(DomainEnums.DisputeActionStatus.OPEN);
        a.setDueAt(req.dueAt());
        a.setAssignedTo(resolveAssignee(req.assignedTo()));
        a.setCreatedBy(actor);

        DisputeStageAction saved = actionRepository.save(a);
        appendAdminUpdate(d, saved.getStage(), actor, "ACTION_CREATED",
                "إجراء جديد: " + saved.getTitle(),
                "النوع: " + saved.getActionType());
        return toResponse(saved);
    }

    public DisputeStageActionResponse updateAction(
            UUID disputeId, UUID actionId, UpdateDisputeStageActionRequest req) {
        DisputeStageAction a = loadAction(disputeId, actionId);
        Person actor = currentUserService.requireCurrentPerson();

        DomainEnums.DisputeActionStatus prevStatus = a.getStatus();

        if (Boolean.TRUE.equals(req.unlinkStage())) {
            a.setStage(null);
        } else if (req.stageId() != null) {
            a.setStage(resolveStage(disputeId, req.stageId()));
        }

        if (req.actionType() != null) {
            a.setActionType(req.actionType());
        }
        if (req.title() != null) {
            a.setTitle(requireText(req.title(), "title"));
        }
        if (req.description() != null) {
            a.setDescription(trimToNull(req.description()));
        }
        if (req.priority() != null) {
            a.setPriority(req.priority());
        }
        if (req.dueAt() != null || Boolean.TRUE.equals(req.clearDueAt())) {
            if (Boolean.TRUE.equals(req.clearDueAt())) {
                a.setDueAt(null);
            } else {
                a.setDueAt(req.dueAt());
            }
        }
        if (req.assignedTo() != null || Boolean.TRUE.equals(req.clearAssignee())) {
            if (Boolean.TRUE.equals(req.clearAssignee())) {
                a.setAssignedTo(null);
            } else {
                a.setAssignedTo(resolveAssignee(req.assignedTo()));
            }
        }

        if (req.status() != null) {
            DomainEnums.DisputeActionStatus ns = req.status();
            if (ns != prevStatus) {
                if (ns == DomainEnums.DisputeActionStatus.DONE) {
                    String note = requireText(req.completionNote(), "completionNote");
                    a.setCompletionNote(note);
                    a.setCompletedBy(actor);
                    applyStatusTransition(a, prevStatus, ns);
                    appendAdminUpdate(a.getDispute(), a.getStage(), actor, "ACTION_COMPLETED",
                            "اكتمل الإجراء: " + a.getTitle(),
                            note);
                } else {
                    applyStatusTransition(a, prevStatus, ns);
                }
            }
            a.setStatus(ns);
        }

        DisputeStageAction saved = actionRepository.save(a);
        return toResponse(saved);
    }

    public DisputeStageActionResponse completeAction(
            UUID disputeId, UUID actionId, CompleteDisputeStageActionRequest req) {
        DisputeStageAction a = loadAction(disputeId, actionId);
        Dispute d = a.getDispute();
        Person actor = currentUserService.requireCurrentPerson();
        String note = requireText(req.completionNote(), "completionNote");

        if (a.getStatus() == DomainEnums.DisputeActionStatus.DONE) {
            return toResponse(a);
        }
        if (a.getStatus() == DomainEnums.DisputeActionStatus.CANCELLED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Cannot complete a cancelled action");
        }

        DomainEnums.DisputeActionStatus prev = a.getStatus();
        a.setCompletionNote(note);
        a.setCompletedBy(actor);
        if (prev != DomainEnums.DisputeActionStatus.DONE) {
            applyStatusTransition(a, prev, DomainEnums.DisputeActionStatus.DONE);
        }
        a.setStatus(DomainEnums.DisputeActionStatus.DONE);
        if (a.getCompletedAt() == null) {
            a.setCompletedAt(LocalDateTime.now());
        }
        DisputeStageAction saved = actionRepository.save(a);
        appendAdminUpdate(d, saved.getStage(), actor, "ACTION_COMPLETED",
                "اكتمل الإجراء: " + saved.getTitle(),
                note);
        return toResponse(saved);
    }

    public DisputeStageActionResponse cancelAction(UUID disputeId, UUID actionId) {
        DisputeStageAction a = loadAction(disputeId, actionId);
        Dispute d = a.getDispute();
        Person actor = currentUserService.requireCurrentPerson();

        if (a.getStatus() == DomainEnums.DisputeActionStatus.CANCELLED) {
            return toResponse(a);
        }

        applyStatusTransition(a, a.getStatus(), DomainEnums.DisputeActionStatus.CANCELLED);
        a.setStatus(DomainEnums.DisputeActionStatus.CANCELLED);
        a.setCompletedAt(null);
        a.setCompletionNote(null);
        a.setCompletedBy(null);
        DisputeStageAction saved = actionRepository.save(a);
        appendAdminUpdate(d, saved.getStage(), actor, "ACTION_CANCELLED",
                "إلغاء الإجراء: " + saved.getTitle(),
                null);
        return toResponse(saved);
    }

    private void appendAdminUpdate(
            Dispute dispute,
            DisputeStage stage,
            Person actor,
            String updateType,
            String title,
            String description) {
        DisputeAdministrativeUpdate u = new DisputeAdministrativeUpdate();
        u.setCompany(dispute.getCompany());
        u.setDispute(dispute);
        u.setStage(stage);
        u.setUpdateType(updateType);
        u.setTitle(title);
        u.setDescription(trimToNull(description));
        u.setOccurredAt(LocalDateTime.now());
        u.setCreatedBy(actor);
        administrativeUpdateRepository.save(u);
    }

    private Dispute loadDispute(UUID id) {
        return disputeRepository.findScopedById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Dispute not found: " + id));
    }

    private DisputeStageAction loadAction(UUID disputeId, UUID actionId) {
        return actionRepository.findByIdAndDispute_Id(actionId, disputeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Action not found: " + actionId));
    }

    private DisputeStage resolveStage(UUID disputeId, UUID stageId) {
        if (stageId == null) {
            return null;
        }
        return disputeStageRepository
                .findByIdAndDispute_Id(stageId, disputeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Stage does not belong to this dispute: " + stageId));
    }

    private Person resolveAssignee(UUID personId) {
        if (personId == null) {
            return null;
        }
        Person p = personRepository.findScopedById(personId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "assignee not found in this company: " + personId));
        if (!p.isActive()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "assignee is inactive: " + personId);
        }
        if (p.getSystemRole() == DomainEnums.SystemRole.CLIENT) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Clients cannot be assigned dispute actions: " + personId);
        }
        return p;
    }

    private static void applyStatusTransition(
            DisputeStageAction a,
            DomainEnums.DisputeActionStatus prev,
            DomainEnums.DisputeActionStatus next) {
        if (next == DomainEnums.DisputeActionStatus.DONE) {
            if (a.getCompletedAt() == null) {
                a.setCompletedAt(LocalDateTime.now());
            }
            return;
        }
        boolean prevDone = prev == DomainEnums.DisputeActionStatus.DONE;
        boolean nextActive = next == DomainEnums.DisputeActionStatus.OPEN
                || next == DomainEnums.DisputeActionStatus.IN_PROGRESS;
        if (prevDone && nextActive) {
            a.setCompletedAt(null);
            a.setCompletionNote(null);
            a.setCompletedBy(null);
            return;
        }
        if (next == DomainEnums.DisputeActionStatus.CANCELLED) {
            a.setCompletedAt(null);
            a.setCompletionNote(null);
            a.setCompletedBy(null);
        }
    }

    private DisputeStageActionResponse toResponse(DisputeStageAction a) {
        LocalDateTime now = LocalDateTime.now();
        boolean overdue = (a.getStatus() == DomainEnums.DisputeActionStatus.OPEN
                || a.getStatus() == DomainEnums.DisputeActionStatus.IN_PROGRESS)
                && a.getDueAt() != null
                && a.getDueAt().isBefore(now);

        return new DisputeStageActionResponse(
                a.getId(),
                a.getDispute().getId(),
                a.getStage() == null ? null : a.getStage().getId(),
                a.getActionType(),
                a.getTitle(),
                a.getDescription(),
                a.getStatus(),
                a.getPriority(),
                a.getDueAt(),
                a.getCompletedAt(),
                a.getCompletionNote(),
                toParty(a.getCompletedBy()),
                toParty(a.getAssignedTo()),
                toParty(a.getCreatedBy()),
                a.getCreatedAt(),
                a.getUpdatedAt(),
                overdue);
    }

    private static PartySummary toParty(Person person) {
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

    private static String requireText(String value, String field) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    field + " is required");
        }
        return trimmed;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
