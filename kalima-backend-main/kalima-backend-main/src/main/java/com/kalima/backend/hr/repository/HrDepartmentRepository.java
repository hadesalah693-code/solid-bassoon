package com.kalima.backend.hr.repository;

import com.kalima.backend.domain.tenant.TenantContext;
import com.kalima.backend.hr.entity.HrDepartment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface HrDepartmentRepository extends JpaRepository<HrDepartment, UUID>,
        JpaSpecificationExecutor<HrDepartment> {

    @Query("SELECT d FROM HrDepartment d WHERE d.id = :id AND d.company.id = :companyId")
    Optional<HrDepartment> findScopedById(@Param("id") UUID id, @Param("companyId") UUID companyId);

    default Optional<HrDepartment> findScopedById(UUID id) {
        return findScopedById(id, TenantContext.getRequired());
    }

    boolean existsByCompany_IdAndName(UUID companyId, String name);

    boolean existsByCompany_IdAndNameAndIdNot(UUID companyId, String name, UUID id);

    @Query("SELECT d FROM HrDepartment d WHERE d.company.id = :companyId AND d.active = true ORDER BY d.name ASC")
    List<HrDepartment> listActive(@Param("companyId") UUID companyId);

    default List<HrDepartment> listActive() {
        return listActive(TenantContext.getRequired());
    }
}
