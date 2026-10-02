package com.kalima.backend.delegation.repository;

import com.kalima.backend.delegation.entity.DelegationReminderDelivery;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface DelegationReminderDeliveryRepository
        extends JpaRepository<DelegationReminderDelivery, DelegationReminderDelivery.Key> {

    boolean existsByCompanyIdAndDelegationIdAndOffsetDaysAndRecipientId(
            UUID companyId, UUID delegationId, int offsetDays, UUID recipientId);
}
