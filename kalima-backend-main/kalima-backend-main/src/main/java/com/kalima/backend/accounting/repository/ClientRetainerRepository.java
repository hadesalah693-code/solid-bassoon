package com.kalima.backend.accounting.repository;

import com.kalima.backend.accounting.entity.ClientRetainer;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ClientRetainerRepository extends JpaRepository<ClientRetainer, UUID> {

    List<ClientRetainer> findByCompanyIdAndClientIdOrderByReceivedDateDesc(UUID companyId, UUID clientId);

    List<ClientRetainer> findByCompanyIdAndStatusOrderByReceivedDateDesc(UUID companyId, ClientRetainer.RetainerStatus status);

    List<ClientRetainer> findByCompanyIdOrderByReceivedDateDesc(UUID companyId);

    @Query("""
        SELECT r FROM ClientRetainer r
        WHERE r.companyId = :companyId
          AND r.clientId = :clientId
          AND r.status = 'ACTIVE'
          AND r.amount > (
              SELECT COALESCE(SUM(a.appliedAmount), 0)
              FROM RetainerApplication a
                            WHERE a.companyId = r.companyId
                                AND a.retainerId = r.id
          )
        ORDER BY r.receivedDate ASC
    """)
    List<ClientRetainer> findActiveRetainersWithBalance(UUID companyId, UUID clientId);

    Optional<ClientRetainer> findByCompanyIdAndReference(UUID companyId, String reference);

    Optional<ClientRetainer> findByIdAndCompanyId(UUID id, UUID companyId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM ClientRetainer r WHERE r.id = :id AND r.companyId = :companyId")
    Optional<ClientRetainer> findForUpdateByIdAndCompanyId(
            @Param("id") UUID id, @Param("companyId") UUID companyId);
}