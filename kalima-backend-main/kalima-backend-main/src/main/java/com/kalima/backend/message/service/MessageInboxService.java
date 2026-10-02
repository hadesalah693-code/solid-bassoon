package com.kalima.backend.message.service;

import com.kalima.backend.auth.support.CurrentUserService;
import com.kalima.backend.casefile.dto.PartySummary;
import com.kalima.backend.casefile.entity.CaseFile;
import com.kalima.backend.casefile.repository.CaseFileRepository;
import com.kalima.backend.domain.entity.DomainEnums;
import com.kalima.backend.domain.entity.Message;
import com.kalima.backend.domain.entity.Person;
import com.kalima.backend.domain.repository.MessageRepository;
import com.kalima.backend.message.dto.MessageInboxItemResponse;
import com.kalima.backend.message.dto.MessageResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Builds the messages inbox in <em>two</em> DB round-trips instead of N+1:
 * <ol>
 *   <li>Load all accessible, non-archived cases in one query.</li>
 *   <li>Batch-fetch last messages and unread counts for all those case IDs.</li>
 * </ol>
 */
@Service
@Transactional(readOnly = true)
public class MessageInboxService {

    private static final Set<DomainEnums.SystemRole> PRIVILEGED_ROLES = EnumSet.of(
            DomainEnums.SystemRole.ADMIN,
            DomainEnums.SystemRole.SENIOR_LAWYER);

    private static final Set<DomainEnums.SystemRole> CHAT_ELIGIBLE_ROLES = EnumSet.of(
            DomainEnums.SystemRole.ADMIN,
            DomainEnums.SystemRole.SENIOR_LAWYER,
            DomainEnums.SystemRole.LAWYER,
            DomainEnums.SystemRole.CLIENT);

    private final CaseFileRepository caseFileRepository;
    private final MessageRepository messageRepository;
    private final CurrentUserService currentUserService;

    public MessageInboxService(CaseFileRepository caseFileRepository,
                               MessageRepository messageRepository,
                               CurrentUserService currentUserService) {
        this.caseFileRepository = caseFileRepository;
        this.messageRepository = messageRepository;
        this.currentUserService = currentUserService;
    }

    public List<MessageInboxItemResponse> inbox() {
        Person actor = requireChatEligible();
        boolean privileged = PRIVILEGED_ROLES.contains(actor.getSystemRole());

        List<CaseFile> cases = caseFileRepository
                .findAllCasesAccessibleForInbox(privileged, actor.getId());

        if (cases.isEmpty()) {
            return List.of();
        }

        List<UUID> caseIds = cases.stream().map(CaseFile::getId).toList();

        // Batch: last message per case (keyed by caseId)
        Map<UUID, Message> lastMsgByCaseId = messageRepository
                .findLastMessagesPerCase(caseIds)
                .stream()
                .collect(Collectors.toMap(m -> m.getCaseFile().getId(), m -> m,
                        (a, b) -> a));   // keep first (already ordered DESC)

        // Batch: unread count per case for current actor as receiver
        Map<UUID, Long> unreadByCaseId = messageRepository
                .countUnreadPerCase(caseIds, actor.getId())
                .stream()
                .collect(Collectors.toMap(
                        row -> (UUID) row[0],
                        row -> (Long) row[1]));

        List<MessageInboxItemResponse> items = cases.stream()
                .map(c -> {
                    Message last = lastMsgByCaseId.get(c.getId());
                    long unread = unreadByCaseId.getOrDefault(c.getId(), 0L);
                    return new MessageInboxItemResponse(
                            c.getId(),
                            c.getCaseNumber(),
                            c.getTitle(),
                            toParty(c.getClient()),
                            toParty(c.getAssignedLawyer()),
                            last != null ? toMessageResponse(last) : null,
                            unread);
                })
                .toList();

        // Sort: cases with messages first (most recent last-message at top),
        // then cases without messages (sorted by case updatedAt DESC).
        return items.stream()
                .sorted(Comparator
                        .<MessageInboxItemResponse, java.time.LocalDateTime>comparing(
                                item -> item.lastMessage() != null
                                        ? item.lastMessage().createdAt()
                                        : null,
                                Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(item -> cases.stream()
                                .filter(c -> c.getId().equals(item.caseId()))
                                .findFirst()
                                .map(CaseFile::getUpdatedAt)
                                .orElse(null),
                                Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    /* ─── helpers ─────────────────────────────────────────────── */

    private Person requireChatEligible() {
        Person actor = currentUserService.requireCurrentPerson();
        if (!CHAT_ELIGIBLE_ROLES.contains(actor.getSystemRole())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Chat is not available for this role.");
        }
        return actor;
    }

    private MessageResponse toMessageResponse(Message m) {
        return new MessageResponse(
                m.getId(),
                m.getCaseFile().getId(),
                toParty(m.getSenderUser()),
                toParty(m.getReceiverUser()),
                m.getSenderType(),
                m.getContent(),
                m.getAttachmentUrl(),
                null,   // replyTo — not needed in inbox preview
                m.isRead(),
                m.getReadAt(),
                m.getCreatedAt(),
                java.util.Map.of(),  // reactions — not loaded in inbox
                null);               // myReaction
    }

    private PartySummary toParty(Person p) {
        if (p == null) {
            return null;
        }
        return new PartySummary(p.getId(), p.getFullName(), p.getSystemRole());
    }
}
