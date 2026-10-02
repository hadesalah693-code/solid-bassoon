package com.kalima.backend.hr.repository;

import com.kalima.backend.domain.tenant.TenantContext;
import com.kalima.backend.hr.entity.HrPosition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface HrPositionRepository extends JpaRepository<HrPosition, UUID>,
        JpaSpecificationExecutor<HrPosition> {

    @Query("SELECT p FROM HrPosition p WHERE p.id = :id AND p.company.id = :companyId")
    Optional<HrPosition> findScopedById(@Param("id") UUID id, @Param("companyId") UUID companyId);

    default Optional<HrPosition> findScopedById(UUID id) {
        return findScopedById(id, TenantContext.getRequired());
    }

    boolean existsByCompany_IdAndTitle(UUID companyId, String title);

    boolean existsByCompany_IdAndTitleAndIdNot(UUID companyId, String title, UUID id);

    @Query("SELECT p FROM HrPosition p WHERE p.company.id = :companyId AND p.active = true ORDER BY p.title ASC")
    List<HrPosition> listActive(@Param("companyId") UUID companyId);

    default List<HrPosition> listActive() {
        return listActive(TenantContext.getRequired());
    }
}
