package com.kalima.backend.mandate.repository;

import com.kalima.backend.domain.entity.DomainEnums;
import com.kalima.backend.mandate.entity.Mandate;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Specification builder for the mandates list filters. */
public final class MandateSpecifications {

    private MandateSpecifications() {
    }

    public static Specification<Mandate> filtered(
            DomainEnums.MandateStatus status,
            DomainEnums.MandatePriority priority,
            DomainEnums.MandatePaymentStatus paymentStatus,
            UUID caseId,
            UUID originalLawyerId,
            UUID substitutedLawyerId,
            String courtName,
            LocalDateTime sessionFrom,
            LocalDateTime sessionTo,
            String search) {
        return (root, query, cb) -> {
            if (Long.class != query.getResultType() && Long.TYPE != query.getResultType()) {
                query.distinct(true);
            }

            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isNull(root.get("deletedAt")));

            if (search != null && !search.isBlank()) {
                String pattern = "%" + search.toLowerCase(Locale.ROOT) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("mandateNumber")), pattern),
                        cb.like(cb.lower(root.get("caseFile").get("caseNumber")), pattern),
                        cb.like(cb.lower(root.get("caseFile").get("title")), pattern),
                        cb.like(cb.lower(root.get("client").get("fullName")), pattern),
                        cb.and(
                                cb.isNotNull(root.get("client").get("companyName")),
                                cb.like(cb.lower(root.get("client").get("companyName")), pattern)),
                        cb.like(cb.lower(root.get("originalLawyer").get("fullName")), pattern),
                        cb.like(cb.lower(root.get("substitutedLawyer").get("fullName")), pattern)));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (priority != null) {
                predicates.add(cb.equal(root.get("priority"), priority));
            }
            if (paymentStatus != null) {
                predicates.add(cb.equal(root.get("paymentStatus"), paymentStatus));
            }
            if (caseId != null) {
                predicates.add(cb.equal(root.get("caseFile").get("id"), caseId));
            }
            if (originalLawyerId != null) {
                predicates.add(cb.equal(root.get("originalLawyer").get("id"), originalLawyerId));
            }
            if (substitutedLawyerId != null) {
                predicates.add(cb.equal(root.get("substitutedLawyer").get("id"), substitutedLawyerId));
            }
            if (courtName != null && !courtName.isBlank()) {
                predicates.add(cb.equal(root.get("courtName"), courtName.trim()));
            }
            if (sessionFrom != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("sessionDate"), sessionFrom));
            }
            if (sessionTo != null) {
                predicates.add(cb.lessThan(root.get("sessionDate"), sessionTo));
            }

            if (predicates.isEmpty()) {
                return cb.conjunction();
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
