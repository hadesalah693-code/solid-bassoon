package com.kalima.backend.realtime.service;

import com.kalima.backend.auth.support.CurrentUserService;
import com.kalima.backend.casefile.service.CaseAccessService;
import com.kalima.backend.domain.entity.Person;
import com.kalima.backend.realtime.dto.TypingEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class TypingNotificationService {

    private final CaseAccessService caseAccessService;
    private final CurrentUserService currentUserService;
    private final MessageBroadcaster broadcaster;

    public TypingNotificationService(CaseAccessService caseAccessService,
                                     CurrentUserService currentUserService,
                                     MessageBroadcaster broadcaster) {
        this.caseAccessService = caseAccessService;
        this.currentUserService = currentUserService;
        this.broadcaster = broadcaster;
    }

    public void notifyTyping(UUID caseId) {
        Person actor = currentUserService.requireCurrentPerson();
        caseAccessService.requireReadableCase(caseId, actor.getId(), actor.getSystemRole());
        String name = actor.getFullName() != null && !actor.getFullName().isBlank()
                ? actor.getFullName()
                : actor.getEmail();
        broadcaster.broadcastTyping(caseId, new TypingEvent(actor.getId(), name));
    }
}
