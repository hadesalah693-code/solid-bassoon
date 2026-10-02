package com.kalima.backend.hr.repository;

import com.kalima.backend.hr.entity.HrPosition;
import org.springframework.data.jpa.domain.Specification;

import java.util.Locale;
import java.util.UUID;

public final class HrPositionSpecifications {

    private HrPositionSpecifications() {
    }

    public static Specification<HrPosition> filtered(UUID departmentId, Boolean active, String search) {
        return (root, query, cb) -> {
            var predicates = cb.conjunction();
            if (departmentId != null) {
                predicates = cb.and(predicates, cb.equal(root.get("department").get("id"), departmentId));
            }
            if (active != null) {
                predicates = cb.and(predicates, cb.equal(root.get("active"), active));
            }
            if (search != null && !search.isBlank()) {
                String pattern = "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
                predicates = cb.and(predicates, cb.or(
                        cb.like(cb.lower(root.get("title")), pattern),
                        cb.like(cb.lower(root.get("code")), pattern)));
            }
            return predicates;
        };
    }
}
