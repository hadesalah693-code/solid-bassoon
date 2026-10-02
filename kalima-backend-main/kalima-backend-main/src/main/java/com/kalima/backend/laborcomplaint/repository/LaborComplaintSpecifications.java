package com.kalima.backend.laborcomplaint.repository;

import com.kalima.backend.laborcomplaint.entity.LaborComplaint;
import com.kalima.backend.laborcomplaint.entity.LaborComplaintParty;
import com.kalima.backend.domain.entity.DomainEnums;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import org.springframework.data.jpa.domain.Specification;

import java.util.Locale;
import java.util.UUID;

public final class LaborComplaintSpecifications {

    private LaborComplaintSpecifications() {
    }

    public static Specification<LaborComplaint> filtered(
            DomainEnums.LaborComplaintStatus status,
            UUID clientId,
            UUID assignedLawyerId,
            String search) {
        return (root, query, cb) -> {
            if (query != null) {
                query.distinct(true);
            }
            var predicates = cb.conjunction();
            if (status != null) {
                predicates = cb.and(predicates, cb.equal(root.get("status"), status));
            }
            if (clientId != null) {
                Join<LaborComplaint, LaborComplaintParty> partyJoin = root.join("parties", JoinType.LEFT);
                predicates = cb.and(predicates, cb.or(
                        cb.equal(root.get("client").get("id"), clientId),
                        cb.and(
                                cb.equal(partyJoin.get("partyKind"), DomainEnums.LaborComplaintPartyKind.CLIENT),
                                cb.equal(partyJoin.get("person").get("id"), clientId))));
            }
            if (assignedLawyerId != null) {
                predicates = cb.and(predicates, cb.equal(root.get("assignedLawyer").get("id"), assignedLawyerId));
            }
            if (search != null && !search.isBlank()) {
                String pattern = "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
                Join<LaborComplaint, LaborComplaintParty> searchPartyJoin = root.join("parties", JoinType.LEFT);
                predicates = cb.and(predicates, cb.or(
                        cb.like(cb.lower(root.get("complaintNumber")), pattern),
                        cb.like(cb.lower(root.get("subject")), pattern),
                        cb.like(cb.lower(root.get("summary")), pattern),
                        cb.like(cb.lower(root.get("opponentName")), pattern),
                        cb.like(cb.lower(root.get("internalReference")), pattern),
                        cb.like(cb.lower(root.get("client").get("fullName")), pattern),
                        cb.like(cb.lower(searchPartyJoin.get("displayName")), pattern),
                        cb.like(cb.lower(searchPartyJoin.get("person").get("fullName")), pattern)));
            }
            return predicates;
        };
    }
}
