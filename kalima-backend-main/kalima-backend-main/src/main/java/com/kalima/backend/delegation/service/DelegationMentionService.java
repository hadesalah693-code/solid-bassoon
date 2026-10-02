package com.kalima.backend.delegation.service;

import com.kalima.backend.delegation.DelegationAgentRoles;
import com.kalima.backend.delegation.entity.Delegation;
import com.kalima.backend.domain.entity.Person;
import com.kalima.backend.domain.repository.PersonRepository;
import com.kalima.backend.notification.service.NotificationService;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class DelegationMentionService {

    private final PersonRepository personRepository;
    private final NotificationService notificationService;

    public DelegationMentionService(PersonRepository personRepository,
                                    NotificationService notificationService) {
        this.personRepository = personRepository;
        this.notificationService = notificationService;
    }

    public void notifyAfterCreate(Delegation delegation, Person actor) {
        List<String> staffNames = staffFullNames();
        notifyNoteMentions(delegation, actor, null, delegation.getNotes(), staffNames);
        notifyNewReminderRecipients(delegation, actor, Set.of(), delegation.getReminderRecipientIds());
    }

    public void notifyAfterUpdate(
            Delegation delegation,
            Person actor,
            String previousNotes,
            Set<UUID> previousRecipientIds) {
        List<String> staffNames = staffFullNames();
        notifyNoteMentions(delegation, actor, previousNotes, delegation.getNotes(), staffNames);
        notifyNewReminderRecipients(
                delegation, actor, previousRecipientIds, delegation.getReminderRecipientIds());
    }

    private void notifyNoteMentions(
            Delegation delegation,
            Person actor,
            String previousNotes,
            String newNotes,
            List<String> staffNames) {
        Set<String> labels = previousNotes == null
                ? new LinkedHashSet<>(DelegationMentionParser.extractMentionLabels(newNotes, staffNames))
                : DelegationMentionParser.labelsOnlyInNewText(previousNotes, newNotes, staffNames);
        if (labels.isEmpty()) {
            return;
        }
        for (Person recipient : resolveStaffByLabels(labels)) {
            if (recipient.getId().equals(actor.getId())) {
                continue;
            }
            notificationService.notifyDelegationMentioned(delegation, recipient, actor, true);
        }
    }

    private void notifyNewReminderRecipients(
            Delegation delegation,
            Person actor,
            Set<UUID> previousRecipientIds,
            Set<UUID> currentRecipientIds) {
        if (currentRecipientIds == null || currentRecipientIds.isEmpty()) {
            return;
        }
        Set<UUID> previous = previousRecipientIds == null ? Set.of() : previousRecipientIds;
        for (UUID recipientId : currentRecipientIds) {
            if (recipientId == null || previous.contains(recipientId)) {
                continue;
            }
            if (recipientId.equals(actor.getId())) {
                continue;
            }
            personRepository.findScopedById(recipientId).ifPresent(recipient ->
                    notificationService.notifyDelegationReminderAdded(delegation, recipient, actor));
        }
    }

    private List<Person> resolveStaffByLabels(Set<String> labels) {
        Set<UUID> seen = new HashSet<>();
        List<Person> resolved = new java.util.ArrayList<>();
        for (String label : labels) {
            for (Person person : personRepository.findActiveStaffByExactFullName(
                    label, DelegationAgentRoles.AGENT_ROLES)) {
                if (seen.add(person.getId())) {
                    resolved.add(person);
                }
            }
        }
        return resolved;
    }

    private List<String> staffFullNames() {
        return personRepository.findActiveStaffByRoles(DelegationAgentRoles.AGENT_ROLES).stream()
                .map(Person::getFullName)
                .filter(name -> name != null && !name.isBlank())
                .map(String::trim)
                .collect(Collectors.toList());
    }
}
