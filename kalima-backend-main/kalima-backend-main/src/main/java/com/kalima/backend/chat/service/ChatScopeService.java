package com.kalima.backend.chat.service;

import com.kalima.backend.auth.support.CurrentUserService;
import com.kalima.backend.casefile.entity.CaseFile;
import com.kalima.backend.casefile.repository.CaseFileRepository;
import com.kalima.backend.chat.dto.ChatScopeCase;
import com.kalima.backend.chat.dto.ChatScopeClient;
import com.kalima.backend.domain.entity.DomainEnums;
import com.kalima.backend.domain.entity.Person;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Returns the lists of clients and cases the authenticated user is
 * authorised to chat with / in. Used by the global chat picker on the
 * frontend.
 *
 * <p><b>Authoritative</b> — the frontend filtering is purely UX; this
 * service is what enforces the rules at request time. The follow-up
 * conversation endpoints ({@code MessageController},
 * {@code CaseChatbotController}) re-check per-case access.
 *
 * <p><b>Rules:</b>
 * <ul>
 *   <li>{@code ADMIN} / {@code SENIOR_LAWYER}: every active client / case
 *       in the tenant.</li>
 *   <li>{@code LAWYER}: only cases where they are the assigned lawyer OR
 *       a row exists in {@code case_participants} for them.</li>
 *   <li>{@code CLIENT}: only their own client record and only their own
 *       cases (defence-in-depth — the global picker UI is hidden for
 *       clients).</li>
 *   <li>{@code SECRETARY} / {@code ACCOUNTANT}: 403 Forbidden — they
 *       cannot send messages per {@code MessageService.ensureCanSend}.</li>
 * </ul>
 */
@Service
@Transactional(readOnly = true)
public class ChatScopeService {

    /**
     * Roles that bypass the per-actor filter (see all clients/cases in
     * the tenant). Mirrors the privileged branch of
     * {@code MessageService.ensureCanRead}.
     */
    private static final Set<DomainEnums.SystemRole> PRIVILEGED_ROLES = EnumSet.of(
            DomainEnums.SystemRole.ADMIN,
            DomainEnums.SystemRole.SENIOR_LAWYER);

    /**
     * Roles that may chat at all. Mirrors {@code @PreAuthorize} on the
     * messages and chatbot controllers.
     */
    private static final Set<DomainEnums.SystemRole> CHAT_ELIGIBLE_ROLES = EnumSet.of(
            DomainEnums.SystemRole.ADMIN,
            DomainEnums.SystemRole.SENIOR_LAWYER,
            DomainEnums.SystemRole.LAWYER,
            DomainEnums.SystemRole.CLIENT);

    private final CaseFileRepository caseFileRepository;
    private final CurrentUserService currentUserService;

    public ChatScopeService(CaseFileRepository caseFileRepository,
                            CurrentUserService currentUserService) {
        this.caseFileRepository = caseFileRepository;
        this.currentUserService = currentUserService;
    }

    public List<ChatScopeClient> listClients() {
        Person actor = requireChatEligible();

        // CLIENT: only themselves. They never pick a different client.
        if (actor.getSystemRole() == DomainEnums.SystemRole.CLIENT) {
            return List.of(toClient(actor));
        }

        boolean privileged = PRIVILEGED_ROLES.contains(actor.getSystemRole());
        List<Person> clients = caseFileRepository
                .findDistinctClientsAccessibleForChat(privileged, actor.getId());
        return clients.stream()
                .filter(p -> p != null)
                .sorted(Comparator
                        .comparing(this::displayLabel, Comparator.nullsLast(String::compareToIgnoreCase)))
                .map(this::toClient)
                .toList();
    }

    public List<ChatScopeCase> listCasesForClient(UUID clientId) {
        if (clientId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "clientId is required");
        }
        Person actor = requireChatEligible();

        // CLIENT: ignore the requested clientId — they only see their own.
        if (actor.getSystemRole() == DomainEnums.SystemRole.CLIENT) {
            if (!actor.getId().equals(clientId)) {
                // Don't leak the existence of any other client.
                return List.of();
            }
            List<CaseFile> own = caseFileRepository
                    .findCasesAccessibleForChatByClient(clientId, false, actor.getId());
            // Fallback for purely-client-flagged cases: ensureCanRead's CLIENT
            // branch only checks c.client.id == actor.id, so any case where
            // the CLIENT is the primary client is always accessible.
            return own.stream().map(this::toCase).toList();
        }

        boolean privileged = PRIVILEGED_ROLES.contains(actor.getSystemRole());
        List<CaseFile> cases = caseFileRepository
                .findCasesAccessibleForChatByClient(clientId, privileged, actor.getId());
        return cases.stream().map(this::toCase).toList();
    }

    /* ─── helpers ──────────────────────────────────────────────── */

    private Person requireChatEligible() {
        Person actor = currentUserService.requireCurrentPerson();
        if (!CHAT_ELIGIBLE_ROLES.contains(actor.getSystemRole())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Chat is not available for this role.");
        }
        return actor;
    }

    private ChatScopeClient toClient(Person p) {
        return new ChatScopeClient(
                p.getId(),
                p.getFullName(),
                p.getCompanyName(),
                p.getEmail());
    }

    private ChatScopeCase toCase(CaseFile c) {
        return new ChatScopeCase(
                c.getId(),
                c.getCaseNumber(),
                c.getTitle(),
                c.getStatus(),
                c.getArchivedAt() != null);
    }

    private String displayLabel(Person p) {
        if (p.getFullName() != null && !p.getFullName().isBlank()) {
            return p.getFullName();
        }
        if (p.getCompanyName() != null && !p.getCompanyName().isBlank()) {
            return p.getCompanyName();
        }
        return p.getEmail() == null ? "" : p.getEmail();
    }
}
