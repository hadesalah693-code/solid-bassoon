package com.kalima.backend.accounting.repository;

import com.kalima.backend.accounting.entity.Budget;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BudgetRepository extends JpaRepository<Budget, UUID> {

    Optional<Budget> findByCompany_IdAndId(UUID companyId, UUID id);

    @Query("""
            SELECT b FROM Budget b
            WHERE b.company.id = :companyId AND b.fiscalYear = :year
            ORDER BY b.periodStart, b.name
            """)
    List<Budget> findByCompanyIdAndYear(@Param("companyId") UUID companyId,
                                         @Param("year") int year);

    @Query("""
            SELECT b FROM Budget b
            WHERE b.company.id = :companyId
            ORDER BY b.periodStart DESC, b.name
            """)
    List<Budget> findAllByCompany(@Param("companyId") UUID companyId);
}