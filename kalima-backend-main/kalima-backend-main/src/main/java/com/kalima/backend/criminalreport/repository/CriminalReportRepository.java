package com.kalima.backend.criminalreport.repository;

import com.kalima.backend.criminalreport.entity.CriminalReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface CriminalReportRepository extends JpaRepository<CriminalReport, UUID>,
        JpaSpecificationExecutor<CriminalReport> {

    @Query("SELECT c FROM CriminalReport c WHERE c.id = :id")
    Optional<CriminalReport> findScopedById(@Param("id") UUID id);

    boolean existsByCompany_IdAndReportNumber(UUID companyId, String reportNumber);

    boolean existsByCompany_IdAndReportNumberAndIdNot(UUID companyId, String reportNumber, UUID id);
}
