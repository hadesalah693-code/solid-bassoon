package com.kalima.backend.message.service;

import com.kalima.backend.auth.support.CurrentUserService;
import com.kalima.backend.casefile.dto.PartySummary;
import com.kalima.backend.casefile.entity.CaseFile;
import com.kalima.backend.casefile.repository.CaseFileRepository;
import com.kalima.backend.casefile.service.CaseAccessService;
import com.kalima.backend.domain.entity.Company;
import com.kalima.backend.domain.entity.DomainEnums;
import com.kalima.backend.domain.entity.Message;
import com.kalima.backend.domain.entity.MessageReaction;
import com.kalima.backend.domain.entity.Person;
import com.kalima.backend.domain.repository.CompanyRepository;
import com.kalima.backend.domain.repository.MessageRepository;
import com.kalima.backend.domain.tenant.TenantContext;
import com.kalima.backend.message.dto.MessagePageResponse;
import com.kalima.backend.message.dto.MessageResponse;
import com.kalima.backend.message.dto.MessageSendRequest;
import com.kalima.backend.message.repository.MessageReactionRepository;
import com.kalima.backend.message.support.MessageCursor;
import com.kalima.backend.notification.service.NotificationService;
import com.kalima.backend.realtime.service.MessageBroadcaster;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class MessageService {

    private final MessageRepository messageRepository;
    private final CaseFileRepository caseFileRepository;
    private final CaseAccessService caseAccessService;
    private final CompanyRepository companyRepository;
    private final CurrentUserService currentUserService;
    private final NotificationService notificationService;
    private final MessageBroadcaster broadcaster;
    private final MessageReactionRepository reactionRepository;
    private final MessageAttachmentService attachmentService;

    public MessageService(MessageRepository messageRepository,
                          CaseFileRepository caseFileRepository,
                          CaseAccessService caseAccessService,
                          CompanyRepository companyRepository,
                          CurrentUserService currentUserService,
                          NotificationService notificationService,
                          MessageBroadcaster broadcaster,
                          MessageReactionRepository reactionRepository,
                          MessageAttachmentService attachmentService) {
        this.messageRepository = messageRepository;
        this.caseFileRepository = caseFileRepository;
        this.caseAccessService = caseAccessService;
        this.companyRepository = companyRepository;
        this.currentUserService = currentUserService;
        this.notificationService = notificationService;
        this.broadcaster = broadcaster;
        this.reactionRepository = reactionRepository;
        this.attachmentService = attachmentService;
    }

    private static final int DEFAULT_PAGE_SIZE = 50;
    private static final int MAX_PAGE_SIZE = 100;

    public MessagePageResponse list(UUID caseId, int limit, String beforeCursor) {
        CaseFile caseFile = loadCase(caseId);
        Person actor = currentUserService.requireCurrentPerson();
        ensureCanRead(caseFile, actor);
        messageRepository.markReadForReceiver(caseId, actor.getId(), LocalDateTime.now());

        int pageSize = normalizePageSize(limit);
        LocalDateTime beforeAt = null;
        UUID beforeId = null;
        if (beforeCursor != null && !beforeCursor.isBlank()) {
            MessageCursor.Point point = MessageCursor.decode(beforeCursor);
            beforeAt = point.createdAt();
            beforeId = point.messageId();
        }

        PageRequest pageRequest = PageRequest.of(0, pageSize + 1);
        List<Message> fetched;
        if (actor.getSystemRole() == DomainEnums.SystemRole.CLIENT) {
            fetched = beforeAt == null
                    ? messageRepository.findParticipantLatestPage(
                            caseId, actor.getId(), pageRequest)
                    : messageRepository.findParticipantOlderPage(
                            caseId, actor.getId(), beforeAt, beforeId, pageRequest);
        } else {
            fetched = beforeAt == null
                    ? messageRepository.findStaffLatestPage(caseId, pageRequest)
                    : messageRepository.findStaffOlderPage(
                            caseId, beforeAt, beforeId, pageRequest);
        }

        boolean hasMore = fetched.size() > pageSize;
        List<Message> page = hasMore ? fetched.subList(0, pageSize) : fetched;
        List<Message> chronological = new ArrayList<>(page);
        Collections.reverse(chronological);

        List<UUID> messageIds = chronological.stream().map(Message::getId).toList();
        Map<UUID, List<MessageReaction>> reactionsByMsg = messageIds.isEmpty()
                ? Map.of()
                : reactionRepository.findByMessageIdIn(messageIds).stream()
                        .collect(Collectors.groupingBy(r -> r.getMessage().getId()));

        List<MessageResponse> items = chronological.stream()
                .map(m -> toResponse(m, reactionsByMsg.getOrDefault(m.getId(), List.of()), actor.getId()))
                .toList();

        String nextCursor = null;
        if (hasMore && !page.isEmpty()) {
            Message oldestInPage = page.get(page.size() - 1);
            nextCursor = MessageCursor.encode(oldestInPage.getCreatedAt(), oldestInPage.getId());
        }

        return new MessagePageResponse(items, hasMore, nextCursor);
    }

    private static int normalizePageSize(int limit) {
        if (limit <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(limit, MAX_PAGE_SIZE);
    }

    public MessageResponse send(UUID caseId, MessageSendRequest request) {
        CaseFile caseFile = loadCase(caseId);
        if (caseFile.getArchivedAt() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Cannot send messages on an archived case.");
        }
        Person actor = currentUserService.requireCurrentPerson();
        ensureCanSend(caseFile, actor);
        if (!request.isValid()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Either content or attachmentUrl is required");
        }
        String content = trimToNull(request.content());
        String attachmentUrl = trimToNull(request.attachmentUrl());
        if (attachmentUrl != null) {
            attachmentUrl = attachmentService.validateAttachmentUrlForSend(
                    caseId, attachmentUrl, actor);
        }
        Person receiver = resolveReceiver(caseFile, actor);
        Message m = new Message();
        m.setCompany(currentTenantReference());
        m.setCaseFile(caseFile);
        m.setSenderUser(actor);
        m.setReceiverUser(receiver);
        m.setSenderType(DomainEnums.MessageSenderType.USER);
        m.setContent(content != null ? content : "");
        m.setAttachmentUrl(attachmentUrl);
        // Reply threading
        if (request.replyToMessageId() != null) {
            Message parent = messageRepository.findScopedById(request.replyToMessageId())
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND, "Reply target not found"));
            if (!parent.getCaseFile().getId().equals(caseId)) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Reply target not found");
            }
            m.setReplyToMessage(parent);
        }
        Message saved = messageRepository.save(m);
        notificationService.notifyNewMessage(caseFile, saved, actor, receiver);
        MessageResponse response = toResponse(saved, List.of(), actor.getId());
        broadcaster.broadcastToCase(caseFile.getId(), response);
        return response;
    }

    private Person resolveReceiver(CaseFile caseFile, Person actor) {
        if (actor.getSystemRole() == DomainEnums.SystemRole.CLIENT) {
            if (caseFile.getAssignedLawyer() != null) {
                return caseFile.getAssignedLawyer();
            }
            return caseFile.getCreatedBy();
        }
        return caseFile.getClient();
    }

    private void ensureCanRead(CaseFile caseFile, Person actor) {
        caseAccessService.requireReadableCase(caseFile, actor);
    }

    private void ensureCanSend(CaseFile caseFile, Person actor) {
        caseAccessService.requireSendableCase(caseFile, actor);
    }

    private CaseFile loadCase(UUID caseId) {
        return caseFileRepository.findScopedById(caseId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Case not found: " + caseId));
    }

    private Company currentTenantReference() {
        return companyRepository.getReferenceById(TenantContext.getRequired());
    }

    private MessageResponse toResponse(Message m,
                                        List<MessageReaction> reactions,
                                        UUID actorId) {
        // Build emoji → count map.
        Map<String, Integer> reactionMap = reactions.stream()
                .collect(Collectors.groupingBy(
                        MessageReaction::getEmoji,
                        Collectors.collectingAndThen(Collectors.counting(), Long::intValue)));

        // Find my own reaction (there can be at most one per emoji, but multiple emojis).
        String myReaction = reactions.stream()
                .filter(r -> r.getPerson().getId().equals(actorId))
                .map(MessageReaction::getEmoji)
                .findFirst()
                .orElse(null);

        // Reply preview.
        MessageResponse.ReplyPreview replyTo = null;
        if (m.getReplyToMessage() != null) {
            Message parent = m.getReplyToMessage();
            String senderName = parent.getSenderUser() != null
                    ? parent.getSenderUser().getFullName()
                    : null;
            String preview = parent.getContent() != null && parent.getContent().length() > 80
                    ? parent.getContent().substring(0, 80) + "…"
                    : parent.getContent();
            replyTo = new MessageResponse.ReplyPreview(parent.getId(), preview, senderName);
        }

        return new MessageResponse(
                m.getId(),
                m.getCaseFile().getId(),
                toParty(m.getSenderUser()),
                toParty(m.getReceiverUser()),
                m.getSenderType(),
                m.getContent(),
                m.getAttachmentUrl(),
                replyTo,
                m.isRead(),
                m.getReadAt(),
                m.getCreatedAt(),
                reactionMap,
                myReaction);
    }

    private PartySummary toParty(Person person) {
        if (person == null) {
            return null;
        }
        return new PartySummary(
                person.getId(),
                person.getFullName(),
                person.getSystemRole());
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
