package com.kalima.backend.laborcomplaint.repository;

import com.kalima.backend.laborcomplaint.entity.LaborComplaint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface LaborComplaintRepository extends JpaRepository<LaborComplaint, UUID>,
        JpaSpecificationExecutor<LaborComplaint> {

    @Query("SELECT lc FROM LaborComplaint lc WHERE lc.id = :id AND lc.company.id = :companyId")
    Optional<LaborComplaint> findScopedById(@Param("id") UUID id, @Param("companyId") UUID companyId);

    default Optional<LaborComplaint> findScopedById(UUID id) {
        return findScopedById(id, com.kalima.backend.domain.tenant.TenantContext.getRequired());
    }

    boolean existsByCompany_IdAndComplaintNumber(UUID companyId, String complaintNumber);

    boolean existsByCompany_IdAndComplaintNumberAndIdNot(UUID companyId, String complaintNumber, UUID id);
}
