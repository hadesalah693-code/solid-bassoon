package com.kalima.backend.hr.repository;

import com.kalima.backend.domain.entity.DomainEnums;
import com.kalima.backend.domain.tenant.TenantContext;
import com.kalima.backend.hr.entity.HrEmployee;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface HrEmployeeRepository extends JpaRepository<HrEmployee, UUID>,
        JpaSpecificationExecutor<HrEmployee> {

    @Query("SELECT e FROM HrEmployee e WHERE e.id = :id AND e.company.id = :companyId")
    Optional<HrEmployee> findScopedById(@Param("id") UUID id, @Param("companyId") UUID companyId);

    default Optional<HrEmployee> findScopedById(UUID id) {
        return findScopedById(id, TenantContext.getRequired());
    }

    boolean existsByCompany_IdAndEmployeeNumber(UUID companyId, String employeeNumber);

    boolean existsByCompany_IdAndEmployeeNumberAndIdNot(UUID companyId, String employeeNumber, UUID id);

    boolean existsByCompany_IdAndDeviceUserId(UUID companyId, String deviceUserId);

    boolean existsByCompany_IdAndDeviceUserIdAndIdNot(UUID companyId, String deviceUserId, UUID id);

    /**
     * Punch-ID → employee lookup used by every sync. Values are matched
     * case-insensitively because device firmware is inconsistent here, and
     * blank/empty punch IDs are ignored on the caller's side.
     */
    @Query("""
            SELECT e FROM HrEmployee e
            WHERE e.company.id = :companyId
              AND lower(trim(e.deviceUserId)) = lower(trim(:deviceUserId))
            """)
    Optional<HrEmployee> findByDeviceUserId(@Param("companyId") UUID companyId,
                                            @Param("deviceUserId") String deviceUserId);

    default Optional<HrEmployee> findByDeviceUserId(String deviceUserId) {
        return findByDeviceUserId(TenantContext.getRequired(), deviceUserId);
    }

    /** Distinct punch IDs already linked to an employee — used to auto-fill forms. */
    @Query("""
            SELECT DISTINCT e.deviceUserId FROM HrEmployee e
            WHERE e.company.id = :companyId
              AND e.deviceUserId IS NOT NULL
              AND length(trim(e.deviceUserId)) > 0
            ORDER BY e.deviceUserId ASC
            """)
    List<String> listDeviceUserIds(@Param("companyId") UUID companyId);

    default List<String> listDeviceUserIds() {
        return listDeviceUserIds(TenantContext.getRequired());
    }

    @Query("SELECT e FROM HrEmployee e WHERE e.company.id = :companyId AND e.status = :status")
    List<HrEmployee> listByStatus(@Param("companyId") UUID companyId,
                                  @Param("status") DomainEnums.HrEmployeeStatus status);

    default List<HrEmployee> listByStatus(DomainEnums.HrEmployeeStatus status) {
        return listByStatus(TenantContext.getRequired(), status);
    }
}
