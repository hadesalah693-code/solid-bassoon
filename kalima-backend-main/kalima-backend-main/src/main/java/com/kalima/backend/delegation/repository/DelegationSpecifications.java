package com.kalima.backend.delegation.repository;

import com.kalima.backend.delegation.entity.Delegation;
import com.kalima.backend.domain.entity.DomainEnums;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class DelegationSpecifications {

    private DelegationSpecifications() {
    }

    public static Specification<Delegation> filtered(
            DomainEnums.DelegationStatus status,
            LocalDate today,
            DomainEnums.DelegationType delegationType,
            UUID clientId,
            UUID agentId,
            String issuingCountry,
            Boolean starredOnly,
            String search) {
        return (root, query, cb) -> {
            if (Long.class != query.getResultType() && Long.TYPE != query.getResultType()) {
                query.distinct(true);
            }

            List<Predicate> predicates = new ArrayList<>();

            if (status == null) {
                // no status filter
            } else if (status == DomainEnums.DelegationStatus.CANCELLED) {
                predicates.add(cb.equal(root.get("status"), DomainEnums.DelegationStatus.CANCELLED));
            } else if (status == DomainEnums.DelegationStatus.EXPIRED) {
                predicates.add(cb.notEqual(root.get("status"), DomainEnums.DelegationStatus.CANCELLED));
                predicates.add(cb.isNotNull(root.get("expiresAt")));
                predicates.add(cb.lessThan(root.get("expiresAt"), today));
            } else if (status == DomainEnums.DelegationStatus.ACTIVE) {
                predicates.add(cb.notEqual(root.get("status"), DomainEnums.DelegationStatus.CANCELLED));
                predicates.add(cb.or(
                        cb.isNull(root.get("expiresAt")),
                        cb.greaterThanOrEqualTo(root.get("expiresAt"), today)));
            } else {
                predicates.add(cb.equal(root.get("status"), status));
            }

            if (delegationType != null) {
                predicates.add(cb.equal(root.get("delegationType"), delegationType));
            }
            if (clientId != null) {
                predicates.add(cb.equal(root.get("client").get("id"), clientId));
            }
            if (agentId != null) {
                predicates.add(cb.or(
                        cb.equal(root.get("agent").get("id"), agentId),
                        cb.isMember(agentId, root.get("agentIds"))));
            }
            if (issuingCountry != null && !issuingCountry.isBlank()) {
                predicates.add(cb.equal(root.get("issuingCountry"), issuingCountry));
            }
            if (Boolean.TRUE.equals(starredOnly)) {
                predicates.add(cb.isTrue(root.get("starred")));
            }
            if (search != null && !search.isBlank()) {
                String pattern = "%" + search.toLowerCase(Locale.ROOT) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("delegationNumber")), pattern),
                        cb.and(
                                cb.isNotNull(root.get("notaryNumber")),
                                cb.like(cb.lower(root.get("notaryNumber")), pattern)),
                        cb.like(cb.lower(root.get("title")), pattern),
                        cb.like(cb.lower(root.get("client").get("fullName")), pattern),
                        cb.and(
                                cb.isNotNull(root.get("client").get("companyName")),
                                cb.like(cb.lower(root.get("client").get("companyName")), pattern)),
                        cb.and(
                                cb.isNotNull(root.get("agent")),
                                cb.like(cb.lower(root.get("agent").get("fullName")), pattern))));
            }

            if (predicates.isEmpty()) {
                return cb.conjunction();
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
