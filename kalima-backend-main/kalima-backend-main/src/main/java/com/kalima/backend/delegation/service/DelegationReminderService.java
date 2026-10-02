package com.kalima.backend.delegation.service;

import com.kalima.backend.delegation.entity.Delegation;
import com.kalima.backend.delegation.entity.DelegationReminderDelivery;
import com.kalima.backend.delegation.repository.DelegationReminderDeliveryRepository;
import com.kalima.backend.delegation.repository.DelegationRepository;
import com.kalima.backend.domain.entity.DomainEnums;
import com.kalima.backend.domain.entity.Person;
import com.kalima.backend.domain.repository.PersonRepository;
import com.kalima.backend.notification.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
public class DelegationReminderService {

    private static final Logger log = LoggerFactory.getLogger(DelegationReminderService.class);
    private static final int CATCH_UP_DAYS = 3;

    private final DelegationRepository delegationRepository;
    private final DelegationReminderDeliveryRepository deliveryRepository;
    private final PersonRepository personRepository;
    private final NotificationService notificationService;

    public DelegationReminderService(DelegationRepository delegationRepository,
                                     DelegationReminderDeliveryRepository deliveryRepository,
                                     PersonRepository personRepository,
                                     NotificationService notificationService) {
        this.delegationRepository = delegationRepository;
        this.deliveryRepository = deliveryRepository;
        this.personRepository = personRepository;
        this.notificationService = notificationService;
    }

    @Transactional
    public int processRemindersForCurrentTenant() {
        LocalDate today = LocalDate.now();
        LocalDate catchUpFrom = today.minusDays(CATCH_UP_DAYS);
        List<Delegation> delegations = delegationRepository.findEligibleForReminders(today);
        int sent = 0;

        for (Delegation delegation : delegations) {
            if (delegation.getStatus() == DomainEnums.DelegationStatus.CANCELLED) {
                continue;
            }
            if (delegation.getReminderOffsets().isEmpty()
                    || delegation.getReminderRecipientIds().isEmpty()) {
                continue;
            }

            for (Integer offsetDays : delegation.getReminderOffsets()) {
                if (offsetDays == null || offsetDays <= 0) {
                    continue;
                }
                LocalDate remindOn = delegation.getExpiresAt().minusDays(offsetDays);
                if (remindOn.isAfter(today)) {
                    continue;
                }
                if (remindOn.isBefore(catchUpFrom)) {
                    continue;
                }

                for (UUID recipientId : delegation.getReminderRecipientIds()) {
                    if (recipientId == null) {
                        continue;
                    }
                    if (deliveryRepository.existsByCompanyIdAndDelegationIdAndOffsetDaysAndRecipientId(
                            delegation.getCompany().getId(), delegation.getId(), offsetDays, recipientId)) {
                        continue;
                    }
                    Person recipient = personRepository.findScopedById(recipientId).orElse(null);
                    if (recipient == null) {
                        continue;
                    }
                    notificationService.notifyDelegationExpiring(
                            delegation, recipient, offsetDays);
                    deliveryRepository.save(new DelegationReminderDelivery(
                            delegation.getCompany().getId(), delegation.getId(), offsetDays, recipientId));
                    sent++;
                }
            }
        }

        if (sent > 0) {
            log.info("Delegation expiry reminders sent: {}", sent);
        }
        return sent;
    }
}
