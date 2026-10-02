package com.kalima.backend.audit.repository;

import com.kalima.backend.audit.entity.AuditLog;
import com.kalima.backend.audit.AuditAction;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Dynamic predicates for {@code /api/audit-logs}. Only non-null filters are
 * added to the WHERE clause, so PostgreSQL always has concrete types for
 * every bound parameter.
 */
public final class AuditLogSpecifications {

    /**
     * Audit entity types written by case-adjacent services that carry the
     * case id in their {@code metadata} JSON payload (see
     * {@code HearingService}, {@code TaskService}, {@code CaseParticipantService},
     * {@code CaseDocumentService}).
     * Kept as string literals to match the exact values stored at write-time.
     */
    private static final String ENTITY_HEARING = "Hearing";
    private static final String ENTITY_TASK = "TaskItem";
    private static final String ENTITY_PARTICIPANT = "CaseParticipant";
    private static final String ENTITY_DOCUMENT = "CaseDocument";

    private AuditLogSpecifications() {
    }

    /**
     * Matches every audit entry that belongs to a given case — both direct
     * {@code CaseFile} events and the adjacent {@code Hearing} / {@code TaskItem}
     * / {@code CaseParticipant} / {@code CaseDocument} events that reference the case in their
     * metadata JSON as {@code "caseId":"<uuid>"}.
     *
     * <p>The metadata column is {@code TEXT}, so a {@code LIKE} with a
     * JSON-aware needle is portable and correct. The preceding entity-type
     * filter limits the scanned rows sharply, and the existing
     * {@code idx_audit_entity} index narrows the {@code CaseFile} branch
     * to an index lookup.
     */
    public static Specification<AuditLog> forCase(UUID caseId) {
        final String needle = "%\"caseId\":\"" + caseId + "\"%";
        return (root, query, cb) -> {
            Predicate caseEntity = cb.and(
                    cb.equal(root.get("entityType"), "CaseFile"),
                    cb.equal(root.get("entityId"), caseId));
            Predicate relatedByMetadata = cb.and(
                    root.get("entityType").in(
                            ENTITY_HEARING, ENTITY_TASK, ENTITY_PARTICIPANT,
                            ENTITY_DOCUMENT),
                    cb.like(root.get("metadata"), needle));
            return cb.or(caseEntity, relatedByMetadata);
        };
    }

    /**
     * Public case timeline for CLIENT users. It intentionally omits internal
     * staff-only work such as tasks, participants, case edits and staff-only
     * documents. Client-visible document rows are matched by their audited
     * metadata visibility.
     */
    public static Specification<AuditLog> forClientVisibleCase(UUID caseId) {
        final String caseNeedle = "%\"caseId\":\"" + caseId + "\"%";
        final String clientVisibleNeedle = "%\"visibility\":\"CLIENT_VISIBLE\"%";
        return (root, query, cb) -> {
            Predicate publicCaseStatus = cb.and(
                    cb.equal(root.get("entityType"), "CaseFile"),
                    cb.equal(root.get("entityId"), caseId),
                    root.get("action").in(
                            AuditAction.CASE_CREATED,
                            AuditAction.CASE_STATUS_CHANGED,
                            AuditAction.CASE_ARCHIVED,
                            AuditAction.CASE_RESTORED));
            Predicate publicHearings = cb.and(
                    cb.equal(root.get("entityType"), ENTITY_HEARING),
                    cb.like(root.get("metadata"), caseNeedle));
            Predicate publicDocuments = cb.and(
                    cb.equal(root.get("entityType"), ENTITY_DOCUMENT),
                    cb.equal(root.get("action"), AuditAction.DOCUMENT_UPLOADED),
                    cb.like(root.get("metadata"), caseNeedle),
                    cb.like(root.get("metadata"), clientVisibleNeedle));
            return cb.or(publicCaseStatus, publicHearings, publicDocuments);
        };
    }

    public static Specification<AuditLog> filters(String action,
                                                  UUID actorId,
                                                  String entityType,
                                                  UUID entityId,
                                                  LocalDateTime from,
                                                  LocalDateTime to,
                                                  Boolean success,
                                                  String q) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (action != null) {
                predicates.add(cb.equal(root.get("action"), action));
            }
            if (actorId != null) {
                predicates.add(cb.equal(root.get("actorPerson").get("id"), actorId));
            }
            if (entityType != null) {
                predicates.add(cb.equal(root.get("entityType"), entityType));
            }
            if (entityId != null) {
                predicates.add(cb.equal(root.get("entityId"), entityId));
            }
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), to));
            }
            if (success != null) {
                predicates.add(cb.equal(root.get("success"), success));
            }
            if (q != null) {
                String t = q.trim();
                if (!t.isEmpty()) {
                    String pattern = "%" + t.toLowerCase() + "%";
                    predicates.add(cb.or(
                            cb.like(cb.lower(root.get("action")), pattern),
                            cb.like(cb.lower(cb.coalesce(
                                    root.get("actorEmail"), cb.literal(""))), pattern),
                            cb.like(cb.lower(cb.coalesce(
                                    root.get("entityType"), cb.literal(""))), pattern),
                            cb.like(cb.lower(cb.coalesce(
                                    root.get("ipAddress"), cb.literal(""))), pattern),
                            cb.like(cb.lower(cb.coalesce(
                                    root.get("userAgent"), cb.literal(""))), pattern),
                            cb.like(cb.lower(cb.coalesce(
                                    root.get("metadata"), cb.literal(""))), pattern),
                            cb.like(cb.lower(cb.coalesce(
                                    root.get("errorMessage"), cb.literal(""))), pattern)
                    ));
                }
            }
            return predicates.isEmpty()
                    ? cb.conjunction()
                    : cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
