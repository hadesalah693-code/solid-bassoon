package com.kalima.backend.hr.repository;

import com.kalima.backend.domain.entity.DomainEnums;
import com.kalima.backend.hr.entity.HrAttendanceDevice;
import org.springframework.data.jpa.domain.Specification;

import java.util.Locale;

public final class HrAttendanceDeviceSpecifications {

    private HrAttendanceDeviceSpecifications() {
    }

    public static Specification<HrAttendanceDevice> filtered(
            DomainEnums.HrDeviceProtocol protocol,
            DomainEnums.HrDeviceStatus status,
            String search) {
        return (root, query, cb) -> {
            var predicates = cb.conjunction();
            if (protocol != null) {
                predicates = cb.and(predicates, cb.equal(root.get("protocol"), protocol));
            }
            if (status != null) {
                predicates = cb.and(predicates, cb.equal(root.get("status"), status));
            }
            if (search != null && !search.isBlank()) {
                String pattern = "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
                predicates = cb.and(predicates, cb.or(
                        cb.like(cb.lower(root.get("name")), pattern),
                        cb.like(cb.lower(root.get("ipAddress")), pattern),
                        cb.like(cb.lower(root.get("location")), pattern),
                        cb.like(cb.lower(root.get("serialNumber")), pattern)));
            }
            return predicates;
        };
    }
}
