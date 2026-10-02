package com.kalima.backend.accounting.repository;

import com.kalima.backend.accounting.entity.RetainerApplication;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RetainerApplicationRepository extends JpaRepository<RetainerApplication, UUID> {

        List<RetainerApplication> findByCompanyIdAndRetainerIdOrderByAppliedDateAsc(
            UUID companyId, UUID retainerId);

    List<RetainerApplication> findByCompanyIdAndTargetTypeAndTargetId(
            UUID companyId, RetainerApplication.TargetType targetType, UUID targetId);

    Optional<RetainerApplication> findByIdAndCompanyId(UUID id, UUID companyId);

    @Query("""
        SELECT COALESCE(SUM(a.appliedAmount), 0)
        FROM RetainerApplication a
                WHERE a.companyId = :companyId
                    AND a.retainerId = :retainerId
    """)
        BigDecimal sumAppliedByRetainer(
                        @Param("companyId") UUID companyId, @Param("retainerId") UUID retainerId);

    @Query("""
        SELECT COALESCE(SUM(a.appliedAmount), 0)
        FROM RetainerApplication a
        WHERE a.companyId = :companyId
          AND a.targetType = :targetType
          AND a.targetId = :targetId
    """)
    BigDecimal sumAppliedToTarget(@Param("companyId") UUID companyId,
                                   @Param("targetType") RetainerApplication.TargetType targetType,
                                   @Param("targetId") UUID targetId);
}