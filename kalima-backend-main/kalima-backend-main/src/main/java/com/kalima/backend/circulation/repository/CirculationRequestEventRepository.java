package com.kalima.backend.circulation.repository;

import com.kalima.backend.circulation.entity.CirculationRequestEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.UUID;
import com.kalima.backend.circulation.entity.CirculationEventType;

public interface CirculationRequestEventRepository extends JpaRepository<CirculationRequestEvent, UUID> {
    @Query("SELECT e FROM CirculationRequestEvent e JOIN FETCH e.actor WHERE e.circulationRequest.id = :requestId AND e.company.id = :companyId ORDER BY e.occurredAt")
    List<CirculationRequestEvent> findByRequest(
            @Param("requestId") UUID requestId, @Param("companyId") UUID companyId);
}