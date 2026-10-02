package com.kalima.backend.hr.repository;

import com.kalima.backend.domain.entity.DomainEnums;
import com.kalima.backend.hr.entity.HrEmployee;
import org.springframework.data.jpa.domain.Specification;

import java.util.Locale;
import java.util.UUID;

public final class HrEmployeeSpecifications {

    private HrEmployeeSpecifications() {
    }

    public static Specification<HrEmployee> filtered(
            UUID departmentId,
            UUID positionId,
            DomainEnums.HrEmployeeStatus status,
            String search) {
        return (root, query, cb) -> {
            var predicates = cb.conjunction();
            if (departmentId != null) {
                predicates = cb.and(predicates, cb.equal(root.get("department").get("id"), departmentId));
            }
            if (positionId != null) {
                predicates = cb.and(predicates, cb.equal(root.get("position").get("id"), positionId));
            }
            if (status != null) {
                predicates = cb.and(predicates, cb.equal(root.get("status"), status));
            }
            if (search != null && !search.isBlank()) {
                String pattern = "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
                predicates = cb.and(predicates, cb.or(
                        cb.like(cb.lower(root.get("fullName")), pattern),
                        cb.like(cb.lower(root.get("employeeNumber")), pattern),
                        cb.like(cb.lower(root.get("deviceUserId")), pattern),
                        cb.like(cb.lower(root.get("phone")), pattern),
                        cb.like(cb.lower(root.get("email")), pattern)));
            }
            return predicates;
        };
    }
}
