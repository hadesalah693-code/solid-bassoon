package com.kalima.backend.hr.repository;

import com.kalima.backend.domain.entity.DomainEnums;
import com.kalima.backend.domain.tenant.TenantContext;
import com.kalima.backend.hr.entity.HrAttendanceDevice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface HrAttendanceDeviceRepository extends JpaRepository<HrAttendanceDevice, UUID>,
        JpaSpecificationExecutor<HrAttendanceDevice> {

    @Query("SELECT d FROM HrAttendanceDevice d WHERE d.id = :id AND d.company.id = :companyId")
    Optional<HrAttendanceDevice> findScopedById(@Param("id") UUID id, @Param("companyId") UUID companyId);

    default Optional<HrAttendanceDevice> findScopedById(UUID id) {
        return findScopedById(id, TenantContext.getRequired());
    }

    boolean existsByCompany_IdAndIpAddress(UUID companyId, String ipAddress);

    boolean existsByCompany_IdAndIpAddressAndIdNot(UUID companyId, String ipAddress, UUID id);

    @Query("""
            SELECT d FROM HrAttendanceDevice d
            WHERE d.company.id = :companyId
              AND d.protocol = :protocol
              AND d.status = :status
            ORDER BY d.name ASC
            """)
    List<HrAttendanceDevice> listPullable(@Param("companyId") UUID companyId,
                                          @Param("protocol") DomainEnums.HrDeviceProtocol protocol,
                                          @Param("status") DomainEnums.HrDeviceStatus status);

    default List<HrAttendanceDevice> listPullable(DomainEnums.HrDeviceProtocol protocol,
                                                  DomainEnums.HrDeviceStatus status) {
        return listPullable(TenantContext.getRequired(), protocol, status);
    }

    /**
     * Resolves the device behind an inbound iClock push. iClock terminals
     * authenticate with their serial number only, so a serial that is not
     * registered must not silently map to some other device.
     */
    @Query("""
            SELECT d FROM HrAttendanceDevice d
            WHERE d.company.id = :companyId
              AND d.serialNumber = :serialNumber
            """)
    Optional<HrAttendanceDevice> findBySerialNumber(@Param("companyId") UUID companyId,
                                                    @Param("serialNumber") String serialNumber);
}
