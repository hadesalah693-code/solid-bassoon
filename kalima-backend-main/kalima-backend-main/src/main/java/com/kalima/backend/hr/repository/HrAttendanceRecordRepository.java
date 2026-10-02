package com.kalima.backend.hr.repository;

import com.kalima.backend.domain.entity.DomainEnums;
import com.kalima.backend.domain.tenant.TenantContext;
import com.kalima.backend.hr.entity.HrAttendanceRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface HrAttendanceRecordRepository extends JpaRepository<HrAttendanceRecord, UUID>,
        JpaSpecificationExecutor<HrAttendanceRecord> {

    @Query("SELECT r FROM HrAttendanceRecord r WHERE r.id = :id AND r.company.id = :companyId")
    Optional<HrAttendanceRecord> findScopedById(@Param("id") UUID id, @Param("companyId") UUID companyId);

    default Optional<HrAttendanceRecord> findScopedById(UUID id) {
        return findScopedById(id, TenantContext.getRequired());
    }

    /**
     * Existence probe used before inserting a pulled punch. The unique index on
     * {@code (device_id, device_user_id, punched_at)} is the real guarantee;
     * this keeps the common "nothing new" case from issuing an INSERT.
     */
    @Query("""
            SELECT COUNT(r) FROM HrAttendanceRecord r
            WHERE r.device.id = :deviceId
              AND lower(trim(r.deviceUserId)) = lower(trim(:deviceUserId))
              AND r.punchedAt = :punchedAt
            """)
    long countExistingPunch(@Param("deviceId") UUID deviceId,
                            @Param("deviceUserId") String deviceUserId,
                            @Param("punchedAt") LocalDateTime punchedAt);

    /** Newest punch known for a device — the high-water mark for the next sync. */
    @Query("SELECT MAX(r.punchedAt) FROM HrAttendanceRecord r WHERE r.device.id = :deviceId")
    LocalDateTime findLastPunchedAt(@Param("deviceId") UUID deviceId);

    /** Punches still waiting for a matching punch ID to be entered. */
    @Query("""
            SELECT COUNT(r) FROM HrAttendanceRecord r
            WHERE r.company.id = :companyId AND r.employee IS NULL
            """)
    long countUnmatched(@Param("companyId") UUID companyId);

    default long countUnmatched() {
        return countUnmatched(TenantContext.getRequired());
    }

    @Query("""
            SELECT r FROM HrAttendanceRecord r
            WHERE r.company.id = :companyId
              AND r.employee IS NULL
              AND r.deviceUserId IN :deviceUserIds
            """)
    List<HrAttendanceRecord> findUnmatchedByDeviceUserIds(
            @Param("companyId") UUID companyId,
            @Param("deviceUserIds") List<String> deviceUserIds);

    default List<HrAttendanceRecord> findUnmatchedByDeviceUserIds(List<String> deviceUserIds) {
        return findUnmatchedByDeviceUserIds(TenantContext.getRequired(), deviceUserIds);
    }

    /**
     * Back-fills previously unmatched punches once an employee gains a punch ID.
     * Scoped to the tenant so a shared punch ID on another tenant is untouched.
     */
    @Query("""
            UPDATE HrAttendanceRecord r
            SET r.employee = :employee
            WHERE r.company.id = :companyId
              AND r.employee IS NULL
              AND lower(trim(r.deviceUserId)) = lower(trim(:deviceUserId))
            """)
    int linkUnmatchedToEmployee(@Param("companyId") UUID companyId,
                                @Param("employee") com.kalima.backend.hr.entity.HrEmployee employee,
                                @Param("deviceUserId") String deviceUserId);

    @Query("""
            SELECT r FROM HrAttendanceRecord r
            WHERE r.company.id = :companyId
              AND r.punchedAt >= :from
              AND r.punchedAt < :to
            ORDER BY r.punchedAt DESC
            """)
    List<HrAttendanceRecord> findRange(@Param("companyId") UUID companyId,
                                       @Param("from") LocalDateTime from,
                                       @Param("to") LocalDateTime to);

    default List<HrAttendanceRecord> findRange(LocalDateTime from, LocalDateTime to) {
        return findRange(TenantContext.getRequired(), from, to);
    }
}
