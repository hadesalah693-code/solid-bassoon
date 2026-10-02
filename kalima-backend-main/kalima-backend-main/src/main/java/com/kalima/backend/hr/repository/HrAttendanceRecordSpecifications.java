package com.kalima.backend.hr.repository;

import com.kalima.backend.domain.entity.DomainEnums;
import com.kalima.backend.hr.entity.HrAttendanceRecord;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.UUID;

public final class HrAttendanceRecordSpecifications {

    private HrAttendanceRecordSpecifications() {
    }

    public static Specification<HrAttendanceRecord> filtered(
            UUID deviceId,
            UUID employeeId,
            DomainEnums.HrPunchType punchType,
            DomainEnums.HrVerifyMode verifyMode,
            Boolean unmatchedOnly,
            LocalDateTime from,
            LocalDateTime to) {
        return (root, query, cb) -> {
            var predicates = cb.conjunction();
            if (deviceId != null) {
                predicates = cb.and(predicates, cb.equal(root.get("device").get("id"), deviceId));
            }
            if (employeeId != null) {
                predicates = cb.and(predicates, cb.equal(root.get("employee").get("id"), employeeId));
            }
            if (punchType != null) {
                predicates = cb.and(predicates, cb.equal(root.get("punchType"), punchType));
            }
            if (verifyMode != null) {
                predicates = cb.and(predicates, cb.equal(root.get("verifyMode"), verifyMode));
            }
            if (Boolean.TRUE.equals(unmatchedOnly)) {
                predicates = cb.and(predicates, cb.isNull(root.get("employee")));
            }
            if (from != null) {
                predicates = cb.and(predicates, cb.greaterThanOrEqualTo(root.get("punchedAt"), from));
            }
            if (to != null) {
                predicates = cb.and(predicates, cb.lessThan(root.get("punchedAt"), to));
            }
            return predicates;
        };
    }
}
