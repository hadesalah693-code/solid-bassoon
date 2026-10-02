package com.kalima.backend.hr.repository;

import com.kalima.backend.domain.entity.DomainEnums;
import com.kalima.backend.hr.entity.HrDepartment;
import org.springframework.data.jpa.domain.Specification;

import java.util.Locale;
import java.util.UUID;

public final class HrDepartmentSpecifications {

    private HrDepartmentSpecifications() {
    }

    public static Specification<HrDepartment> filtered(Boolean active, String search) {
        return (root, query, cb) -> {
            var predicates = cb.conjunction();
            if (active != null) {
                predicates = cb.and(predicates, cb.equal(root.get("active"), active));
            }
            if (search != null && !search.isBlank()) {
                String pattern = "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
                predicates = cb.and(predicates, cb.or(
                        cb.like(cb.lower(root.get("name")), pattern),
                        cb.like(cb.lower(root.get("code")), pattern)));
            }
            return predicates;
        };
    }
}
