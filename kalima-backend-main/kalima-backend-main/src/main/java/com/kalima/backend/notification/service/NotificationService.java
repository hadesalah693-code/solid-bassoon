package com.kalima.backend.notification.service;

import com.kalima.backend.auth.support.CurrentUserService;
import com.kalima.backend.casefile.entity.CaseFile;
import com.kalima.backend.calendar.entity.CalendarEvent;
import com.kalima.backend.circulation.entity.CirculationRequest;
import com.kalima.backend.delegation.entity.Delegation;
import com.kalima.backend.domain.entity.Company;
import com.kalima.backend.domain.entity.DomainEnums;
import com.kalima.backend.domain.entity.Message;
import com.kalima.backend.domain.entity.Person;
import com.kalima.backend.dispute.entity.Dispute;
import com.kalima.backend.hearing.entity.Hearing;
import com.kalima.backend.mandate.entity.Mandate;
import com.kalima.backend.task.entity.TaskItem;
import com.kalima.backend.notification.entity.Notification;
import com.kalima.backend.notification.repository.NotificationRepository;
import com.kalima.backend.notification.dto.NotificationResponse;
import com.kalima.backend.notification.dto.UnreadCountResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

@Service
@Transactional
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final CurrentUserService currentUserService;

    public NotificationService(NotificationRepository notificationRepository,
                               CurrentUserService currentUserService) {
        this.notificationRepository = notificationRepository;
        this.currentUserService = currentUserService;
    }

    @Transactional(readOnly = true)
    public Page<NotificationResponse> listForCurrentUser(Pageable pageable) {
        UUID me = currentUserService.requireCurrentPerson().getId();
        return notificationRepository
                .findByRecipientId(me, pageable)
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public UnreadCountResponse unreadCount() {
        UUID me = currentUserService.requireCurrentPerson().getId();
        return new UnreadCountResponse(
                notificationRepository.countByRecipientUser_IdAndReadIsFalse(me));
    }

    public void markRead(UUID id) {
        Person me = currentUserService.requireCurrentPerson();
        Notification n = notificationRepository
                .findByIdForRecipient(id, me.getId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Notification not found: " + id));
        if (!n.isRead()) {
            n.setRead(true);
            n.setReadAt(LocalDateTime.now());
        }
    }

    public int markAllRead() {
        UUID me = currentUserService.requireCurrentPerson().getId();
        return notificationRepository.markAllReadForRecipient(
                me, LocalDateTime.now());
    }

    // ----- internal: domain event hooks -----------------------------------

    public void notifyHearingScheduled(CaseFile caseFile, Hearing saved, Person actor) {
        Objects.requireNonNull(caseFile, "caseFile");
        Objects.requireNonNull(saved, "hearing");
        Person target = caseFile.getAssignedLawyer() != null
                ? caseFile.getAssignedLawyer()
                : caseFile.getClient();
        if (target.getId().equals(actor.getId())) {
            return;
        }
        String title = "جلسة جديدة";
        String body = "قضية " + caseFile.getCaseNumber() + " — " + saved.getHearingDate();
        persist(
                caseFile.getCompany(),
                target,
                caseFile,
                DomainEnums.NotificationType.HEARING_ADDED,
                title,
                body);
    }

    public void notifyHearingUpdated(CaseFile caseFile, Hearing h, Person actor) {
        Person target = caseFile.getAssignedLawyer() != null
                ? caseFile.getAssignedLawyer()
                : caseFile.getClient();
        if (target.getId().equals(actor.getId())) {
            return;
        }
        String title = "تحديث جلسة";
        String body = "قضية " + caseFile.getCaseNumber() + " — " + h.getHearingDate();
        persist(
                caseFile.getCompany(),
                target,
                caseFile,
                DomainEnums.NotificationType.HEARING_UPDATED,
                title,
                body);
    }

    public void notifyCaseStatusChanged(
            CaseFile c,
            DomainEnums.CaseStatus oldStatus,
            Person actor) {
        Person client = c.getClient();
        if (client.getId().equals(actor.getId())) {
            return;
        }
        String title = "تغيير حالة قضية";
        String body = c.getCaseNumber() + ": " + oldStatus.name() + " → " + c.getStatus().name();
        persist(
                c.getCompany(),
                client,
                c,
                DomainEnums.NotificationType.CASE_STATUS_CHANGED,
                title,
                body);
    }

    public void notifyNewMessage(CaseFile c, Message message, Person actor, Person recipient) {
        if (recipient.getId().equals(actor.getId())) {
            return;
        }
        String title = "رسالة جديدة";
        String body = c.getCaseNumber() + ": " + preview(message.getContent());
        persist(
                c.getCompany(),
                recipient,
                c,
                DomainEnums.NotificationType.NEW_MESSAGE,
                title,
                body);
    }

    /** Agent-created notification (voice or night agent). */
    public void createAgentNotification(
            Person recipient,
            CaseFile caseFile,
            String title,
            String body) {
        Objects.requireNonNull(recipient, "recipient");
        persist(
                recipient.getCompany(),
                recipient,
                caseFile,
                DomainEnums.NotificationType.AGENT_REMINDER,
                title,
                body);
    }

    public void createNightInsightNotification(
            Person recipient,
            CaseFile caseFile,
            String title,
            String body) {
        Objects.requireNonNull(recipient, "recipient");
        persist(
                recipient.getCompany(),
                recipient,
                caseFile,
                DomainEnums.NotificationType.AGENT_NIGHT_INSIGHT,
                title,
                body);
    }

    public void notifyTaskAssigned(TaskItem task, Person actor) {
        Objects.requireNonNull(task, "task");
        Person assignee = task.getAssignedTo();
        if (assignee == null || assignee.getId().equals(actor.getId())) {
            return;
        }
        CaseFile caseFile = task.getCaseFile();
        String title = "مهمة جديدة";
        StringBuilder body = new StringBuilder();
        if (caseFile != null) {
            body.append("قضية ").append(caseFile.getCaseNumber()).append(" — ");
        } else if (task.getDispute() != null) {
            body.append("نزاع ").append(task.getDispute().getDisputeNumber()).append(" — ");
        }
        body.append(task.getTitle());
        persist(
                task.getCompany(),
                assignee,
                caseFile,
                DomainEnums.NotificationType.TASK_ASSIGNED,
                title,
                body.toString());
    }

    public void notifyTaskDueSoon(TaskItem task) {
        Person assignee = task.getAssignedTo();
        if (assignee == null) {
            return;
        }
        CaseFile caseFile = task.getCaseFile();
        String title = "موعد المهمة غداً";
        String body = taskReminderBody(task);
        persist(
                task.getCompany(),
                assignee,
                caseFile,
                DomainEnums.NotificationType.TASK_DUE_SOON,
                title,
                body);
    }

    public void notifyTaskOverdue(TaskItem task) {
        Person assignee = task.getAssignedTo();
        if (assignee == null) {
            return;
        }
        CaseFile caseFile = task.getCaseFile();
        String title = "مهمة متأخرة";
        String body = taskReminderBody(task);
        persist(
                task.getCompany(),
                assignee,
                caseFile,
                DomainEnums.NotificationType.TASK_OVERDUE,
                title,
                body);
    }

    public void notifyCalendarEventAssigned(CalendarEvent event, Person recipient, Person actor) {
        if (recipient.getId().equals(actor.getId())) {
            return;
        }
        CaseFile caseFile = event.getCaseFile();
        String title = "حدث تقويم جديد";
        String body = event.getTitle() + " — " + event.getStartAt();
        persist(
                event.getCompany(),
                recipient,
                caseFile,
                DomainEnums.NotificationType.CALENDAR_EVENT_ASSIGNED,
                title,
                body);
    }

    public void notifyCalendarReminder(CalendarEvent event, Person recipient, int offsetMinutes) {
        CaseFile caseFile = event.getCaseFile();
        String title = "تذكير تقويم";
        String body = event.getTitle() + " — يبدأ خلال " + formatOffset(offsetMinutes);
        persist(
                event.getCompany(),
                recipient,
                caseFile,
                DomainEnums.NotificationType.CALENDAR_REMINDER,
                title,
                body);
    }

    public void notifyDelegationExpiring(Delegation delegation, Person recipient, int offsetDays) {
        Objects.requireNonNull(delegation, "delegation");
        Objects.requireNonNull(recipient, "recipient");
        String title = "تذكير انتهاء توكيل";
        String body = delegation.getDelegationNumber()
                + " — "
                + delegation.getTitle()
                + " ينتهي خلال "
                + offsetDays
                + " يوم ("
                + delegation.getExpiresAt()
                + ")";
        persist(
                delegation.getCompany(),
                recipient,
                null,
                DomainEnums.NotificationType.DELEGATION_EXPIRING,
                title,
                body);
    }

    public void notifyDelegationCancelled(Delegation delegation, Person actor) {
        Objects.requireNonNull(delegation, "delegation");
        Objects.requireNonNull(actor, "actor");
        if (delegation.getClient() == null
                || delegation.getClient().getId().equals(actor.getId())) {
            return;
        }
        persist(
                delegation.getCompany(),
                delegation.getClient(),
                null,
                DomainEnums.NotificationType.DELEGATION_CANCELLED,
                "إلغاء توكيل",
                delegation.getDelegationNumber() + " — " + delegation.getTitle());
    }

    public void notifyCirculationUpdate(
            CirculationRequest request, Person recipient, String action) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(recipient, "recipient");
        persist(request.getCompany(), recipient, request.getCaseFile(),
                DomainEnums.NotificationType.CIRCULATION_REQUEST,
                "تداول ملف قضية — " + action,
                request.getCaseFile().getCaseNumber() + " — " + request.getReason());
    }

    public void notifyDelegationMentioned(Delegation delegation, Person recipient, Person actor, boolean inNotes) {
        Objects.requireNonNull(delegation, "delegation");
        Objects.requireNonNull(recipient, "recipient");
        if (recipient.getId().equals(actor.getId())) {
            return;
        }
        String actorName = actor.getFullName() != null ? actor.getFullName().trim() : actor.getEmail();
        String title = "إشارة في توكيل";
        String body = (actorName != null && !actorName.isBlank() ? actorName + " — " : "")
                + delegation.getDelegationNumber()
                + " — "
                + delegation.getTitle();
        if (inNotes && delegation.getNotes() != null && !delegation.getNotes().isBlank()) {
            body += " — " + preview(delegation.getNotes());
        }
        persist(
                delegation.getCompany(),
                recipient,
                null,
                DomainEnums.NotificationType.DELEGATION_MENTION,
                title,
                body);
    }

    public void notifyDelegationReminderAdded(Delegation delegation, Person recipient, Person actor) {
        Objects.requireNonNull(delegation, "delegation");
        Objects.requireNonNull(recipient, "recipient");
        if (recipient.getId().equals(actor.getId())) {
            return;
        }
        String actorName = actor.getFullName() != null ? actor.getFullName().trim() : actor.getEmail();
        String title = "تذكيرات توكيل";
        String body = (actorName != null && !actorName.isBlank() ? actorName + " أضافك — " : "")
                + delegation.getDelegationNumber()
                + " — "
                + delegation.getTitle();
        if (delegation.getExpiresAt() != null) {
            body += " (ينتهي " + delegation.getExpiresAt() + ")";
        }
        persist(
                delegation.getCompany(),
                recipient,
                null,
                DomainEnums.NotificationType.DELEGATION_REMINDER_ADDED,
                title,
                body);
    }

    public void notifyDisputeLawyerChanged(
            Dispute dispute,
            Person previousLawyer,
            Person newLawyer,
            Person administrativeOfficer,
            Person actor) {
        Objects.requireNonNull(dispute, "dispute");
        String ref = dispute.getDisputeNumber() != null
                ? dispute.getDisputeNumber()
                : dispute.getId().toString();
        String newName = newLawyer != null ? personLabel(newLawyer) : "—";
        String title = "تغيير المحامي المسؤول للنزاع";
        String body = "نزاع " + ref + " (" + dispute.getTitle() + ") — المحامي الجديد: " + newName;

        if (previousLawyer != null && !previousLawyer.getId().equals(actor.getId())) {
            persist(
                    dispute.getCompany(),
                    previousLawyer,
                    null,
                    DomainEnums.NotificationType.DISPUTE_LAWYER_CHANGED,
                    title,
                    body + " — تم إزالتك من المسؤولية.");
        }
        if (administrativeOfficer != null
                && !administrativeOfficer.getId().equals(actor.getId())
                && (previousLawyer == null
                        || !administrativeOfficer.getId().equals(previousLawyer.getId()))) {
            persist(
                    dispute.getCompany(),
                    administrativeOfficer,
                    null,
                    DomainEnums.NotificationType.DISPUTE_LAWYER_CHANGED,
                    title,
                    body);
        }
    }

    /** New mandate assigned to a substituted lawyer. */
    public void notifyMandateCreated(Mandate mandate, Person actor) {
        Person target = mandate.getSubstitutedLawyer();
        if (target.getId().equals(actor.getId())) {
            return;
        }
        String title = "إنابة جديدة";
        String body = mandate.getMandateNumber()
                + " — قضية "
                + mandate.getCaseFile().getCaseNumber()
                + (mandate.getSessionDate() != null ? " — جلسة " + mandate.getSessionDate() : "");
        persist(
                mandate.getCompany(),
                target,
                mandate.getCaseFile(),
                DomainEnums.NotificationType.MANDATE_CREATED,
                title,
                body);
    }

    /** Mandate confirmed (substituted lawyer accepted). */
    public void notifyMandateConfirmed(Mandate mandate, Person actor) {
        Person target = mandate.getOriginalLawyer();
        if (target.getId().equals(actor.getId())) {
            return;
        }
        String title = "تم تأكيد الإنابة";
        String body = mandate.getMandateNumber() + " — " + mandate.getCaseFile().getCaseNumber();
        persist(
                mandate.getCompany(),
                target,
                mandate.getCaseFile(),
                DomainEnums.NotificationType.MANDATE_CONFIRMED,
                title,
                body);
    }

    /** Mandate cancelled. */
    public void notifyMandateCancelled(Mandate mandate, Person actor) {
        String title = "إلغاء إنابة";
        String body = mandate.getMandateNumber()
                + " — "
                + mandate.getCaseFile().getCaseNumber()
                + (mandate.getCancelledReason() != null
                        ? " — " + preview(mandate.getCancelledReason())
                        : "");
        Person substituted = mandate.getSubstitutedLawyer();
        if (substituted != null && !substituted.getId().equals(actor.getId())) {
            persist(
                    mandate.getCompany(),
                    substituted,
                    mandate.getCaseFile(),
                    DomainEnums.NotificationType.MANDATE_CANCELLED,
                    title,
                    body);
        }
    }

    /** Session result recorded, needs review by the original lawyer. */
    public void notifyMandateSessionResult(Mandate mandate) {
        String title = "نتيجة جلسة إنابة";
        String body = mandate.getMandateNumber()
                + " — "
                + mandate.getCaseFile().getCaseNumber()
                + (mandate.getSessionResult() != null
                        ? " — " + preview(mandate.getSessionResult())
                        : "");
        persist(
                mandate.getCompany(),
                mandate.getOriginalLawyer(),
                mandate.getCaseFile(),
                DomainEnums.NotificationType.MANDATE_SESSION_RESULT,
                title,
                body);
    }

    /** Session approaching soon — reminder to the substituted lawyer. */
    public void notifyMandateSessionApproaching(Mandate mandate, Person recipient, int offsetDays) {
        String title = "اقتراب جلسة إنابة";
        String body = mandate.getMandateNumber()
                + " — "
                + mandate.getCaseFile().getCaseNumber()
                + " خلال "
                + offsetDays
                + " يوم ("
                + mandate.getSessionDate()
                + ")";
        persist(
                mandate.getCompany(),
                recipient,
                mandate.getCaseFile(),
                DomainEnums.NotificationType.MANDATE_SESSION_APPROACHING,
                title,
                body);
    }

    private static String personLabel(Person p) {
        if (p.getFullName() != null && !p.getFullName().isBlank()) {
            return p.getFullName().trim();
        }
        return p.getEmail() != null ? p.getEmail() : p.getId().toString();
    }

    private static String formatOffset(int minutes) {
        if (minutes >= 1440 && minutes % 1440 == 0) {
            int days = minutes / 1440;
            return days == 7 ? "أسبوع" : days + " يوم";
        }
        if (minutes >= 60 && minutes % 60 == 0) {
            return (minutes / 60) + " ساعة";
        }
        return minutes + " دقيقة";
    }

    private String taskReminderBody(TaskItem task) {
        StringBuilder body = new StringBuilder();
        if (task.getCaseFile() != null) {
            body.append("قضية ").append(task.getCaseFile().getCaseNumber()).append(" — ");
        } else if (task.getDispute() != null) {
            body.append("نزاع ").append(task.getDispute().getDisputeNumber()).append(" — ");
        }
        body.append(task.getTitle());
        if (task.getDueDate() != null) {
            body.append(" — ").append(task.getDueDate());
        }
        return body.toString();
    }

    private void persist(
            Company company,
            Person recipient,
            CaseFile caseFile,
            DomainEnums.NotificationType type,
            String title,
            String body) {
        Notification n = new Notification();
        n.setCompany(company);
        n.setRecipientUser(recipient);
        n.setCaseFile(caseFile);
        n.setType(type);
        n.setTitle(title);
        n.setBody(body);
        n.setRead(false);
        notificationRepository.save(n);
    }

    private NotificationResponse toResponse(Notification n) {
        return new NotificationResponse(
                n.getId(),
                n.getType(),
                n.getTitle(),
                n.getBody(),
                n.isRead(),
                n.getCaseFile() != null ? n.getCaseFile().getId() : null,
                n.getCreatedAt());
    }

    private String preview(String content) {
        if (content == null) {
            return "";
        }
        String trimmed = content.trim();
        return trimmed.length() <= 120 ? trimmed : trimmed.substring(0, 120) + "...";
    }
}
