package com.kalima.backend.accounting.repository;

import com.kalima.backend.accounting.entity.FinancialPeriod;
import com.kalima.backend.accounting.entity.FinancialPeriod.Status;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FinancialPeriodRepository extends JpaRepository<FinancialPeriod, UUID> {

    @EntityGraph(attributePaths = "closedBy")
    List<FinancialPeriod> findByCompany_IdOrderByStartDateDesc(UUID companyId);

    @EntityGraph(attributePaths = "closedBy")
    Optional<FinancialPeriod> findByCompany_IdAndId(UUID companyId, UUID id);

    Optional<FinancialPeriod> findByCompany_IdAndName(UUID companyId, String name);

    Optional<FinancialPeriod> findFirstByCompany_IdAndStatusOrderByStartDateDesc(UUID companyId, Status status);

    @Query("""
            select p from FinancialPeriod p
            where p.company.id = :companyId
              and :date between p.startDate and p.endDate
            order by p.startDate desc
            """)
    Optional<FinancialPeriod> findByDate(@Param("companyId") UUID companyId,
                                        @Param("date") java.time.LocalDate date);

    boolean existsByCompany_IdAndName(UUID companyId, String name);
}
