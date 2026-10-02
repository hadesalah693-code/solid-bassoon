package com.kalima.backend.message.service;

import com.kalima.backend.auth.support.CurrentUserService;
import com.kalima.backend.casefile.service.CaseAccessService;
import com.kalima.backend.domain.entity.Company;
import com.kalima.backend.domain.entity.Message;
import com.kalima.backend.domain.entity.MessageReaction;
import com.kalima.backend.domain.entity.Person;
import com.kalima.backend.domain.repository.CompanyRepository;
import com.kalima.backend.domain.repository.MessageRepository;
import com.kalima.backend.domain.tenant.TenantContext;
import com.kalima.backend.message.repository.MessageReactionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Toggle an emoji reaction on a message (add if absent, remove if present).
 */
@Service
@Transactional
public class MessageReactionService {

    private final MessageReactionRepository reactionRepository;
    private final MessageRepository messageRepository;
    private final CaseAccessService caseAccessService;
    private final CompanyRepository companyRepository;
    private final CurrentUserService currentUserService;

    public MessageReactionService(MessageReactionRepository reactionRepository,
                                  MessageRepository messageRepository,
                                  CaseAccessService caseAccessService,
                                  CompanyRepository companyRepository,
                                  CurrentUserService currentUserService) {
        this.reactionRepository = reactionRepository;
        this.messageRepository  = messageRepository;
        this.caseAccessService = caseAccessService;
        this.companyRepository  = companyRepository;
        this.currentUserService = currentUserService;
    }

    public record ReactionResult(Map<String, Integer> reactions, String myReaction) {}

    /**
     * Toggle the given emoji for the authenticated user on {@code messageId}.
     * Validates that the message belongs to a case the actor can access.
     *
     * @return updated reaction map + actor's current reaction (null if removed)
     */
    public ReactionResult toggle(UUID caseId, UUID messageId, String emoji) {
        if (emoji == null || emoji.isBlank() || emoji.length() > 10) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid emoji");
        }

        Person actor = currentUserService.requireCurrentPerson();
        caseAccessService.requireReadableCase(caseId, actor.getId(), actor.getSystemRole());

        Message message = messageRepository.findScopedById(messageId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Message not found: " + messageId));
        if (!message.getCaseFile().getId().equals(caseId)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Message not found: " + messageId);
        }

        var existing = reactionRepository.findByMessage_IdAndPerson_IdAndEmoji(
                messageId, actor.getId(), emoji);

        if (existing.isPresent()) {
            // Toggle off.
            reactionRepository.deleteByMessage_IdAndPerson_IdAndEmoji(messageId, actor.getId(), emoji);
        } else {
            // Toggle on.
            Company company = companyRepository.getReferenceById(TenantContext.getRequired());
            MessageReaction reaction = new MessageReaction();
            reaction.setCompany(company);
            reaction.setMessage(message);
            reaction.setPerson(actor);
            reaction.setEmoji(emoji);
            reactionRepository.save(reaction);
        }

        // Reload all reactions for this message to build the updated map.
        List<MessageReaction> all = reactionRepository.findByMessageIdIn(List.of(messageId));
        Map<String, Integer> map = all.stream()
                .collect(Collectors.groupingBy(
                        MessageReaction::getEmoji,
                        Collectors.collectingAndThen(Collectors.counting(), Long::intValue)));

        String myReaction = all.stream()
                .filter(r -> r.getPerson().getId().equals(actor.getId()))
                .map(MessageReaction::getEmoji)
                .findFirst()
                .orElse(null);

        return new ReactionResult(map, myReaction);
    }
}
