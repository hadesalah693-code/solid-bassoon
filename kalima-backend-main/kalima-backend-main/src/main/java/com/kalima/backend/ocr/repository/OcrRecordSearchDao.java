package com.kalima.backend.ocr.repository;

import com.kalima.backend.domain.entity.DomainEnums;
import com.kalima.backend.ocr.dto.OcrSearchParams;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Native SQL search for OCR records with PostgreSQL FTS and relation filters.
 */
@Repository
public class OcrRecordSearchDao {

    private static final Map<String, String> SORT_COLUMNS = Map.of(
            "createdAt", "r.created_at",
            "title", "r.title",
            "status", "r.status",
            "processingCompletedAt", "r.processing_completed_at",
            "updatedAt", "r.updated_at");

    @PersistenceContext
    private EntityManager entityManager;

    public Page<UUID> searchIds(
            UUID companyId,
            UUID personId,
            boolean privileged,
            OcrSearchParams params,
            Pageable pageable) {

        StringBuilder where = new StringBuilder("""
                FROM ocr_records r
                WHERE r.company_id = :companyId
                """);

        Map<String, Object> bind = new HashMap<>();
        bind.put("companyId", companyId);
        bind.put("personId", personId);
        bind.put("privileged", privileged);

        appendAccessClause(where);
        appendFilters(where, bind, params);

        String countSql = "SELECT COUNT(DISTINCT r.id) " + where;
        Query countQuery = entityManager.createNativeQuery(countSql);
        bind.forEach(countQuery::setParameter);
        long total = ((Number) countQuery.getSingleResult()).longValue();
        if (total == 0) {
            return Page.empty(pageable);
        }

        String orderBy = resolveOrderBy(pageable);
        String dataSql = "SELECT DISTINCT r.id " + where + orderBy;
        Query dataQuery = entityManager.createNativeQuery(dataSql);
        bind.forEach(dataQuery::setParameter);
        dataQuery.setFirstResult((int) pageable.getOffset());
        dataQuery.setMaxResults(pageable.getPageSize());

        @SuppressWarnings("unchecked")
        List<UUID> ids = ((List<Object>) dataQuery.getResultList()).stream()
                .map(id -> id instanceof UUID u ? u : UUID.fromString(id.toString()))
                .toList();

        return new PageImpl<>(ids, pageable, total);
    }

    private static void appendAccessClause(StringBuilder where) {
        where.append("""
                 AND (
                   :privileged = TRUE
                   OR r.uploaded_by = :personId
                   OR EXISTS (
                     SELECT 1 FROM ocr_relations rel
                     WHERE rel.ocr_record_id = r.id
                       AND rel.relation_type = 'CASE'
                       AND (
                         EXISTS (
                           SELECT 1 FROM cases c
                           WHERE c.id = rel.relation_id
                             AND c.assigned_lawyer_id = :personId
                         )
                         OR EXISTS (
                           SELECT 1 FROM case_participants cp
                           WHERE cp.case_id = rel.relation_id
                             AND cp.person_id = :personId
                         )
                       )
                   )
                 )
                """);
    }

    private static void appendFilters(StringBuilder where, Map<String, Object> bind, OcrSearchParams params) {
        if (params.status() != null) {
            where.append(" AND r.status = :status");
            bind.put("status", params.status().name());
        }
        if (params.documentType() != null) {
            where.append(" AND r.document_type = :documentType");
            bind.put("documentType", params.documentType().name());
        }
        if (params.uploadedBy() != null) {
            where.append(" AND r.uploaded_by = :uploadedBy");
            bind.put("uploadedBy", params.uploadedBy());
        }
        LocalDateTime fromAt = params.from() == null ? null : params.from().atStartOfDay();
        LocalDateTime toAt = params.to() == null ? null : params.to().plusDays(1).atStartOfDay();
        if (fromAt != null) {
            where.append(" AND r.created_at >= :fromAt");
            bind.put("fromAt", fromAt);
        }
        if (toAt != null) {
            where.append(" AND r.created_at < :toAt");
            bind.put("toAt", toAt);
        }
        if (params.q() != null && !params.q().isBlank()) {
            where.append("""
                     AND to_tsvector(
                           'simple',
                           coalesce(r.title, '') || ' ' ||
                           coalesce(r.edited_text, coalesce(r.extracted_text, ''))
                         ) @@ plainto_tsquery('simple', :q)
                    """);
            bind.put("q", sanitizeQuery(params.q()));
        }
        appendRelationFilter(where, bind, "CASE", params.caseId(), "caseId");
        appendRelationFilter(where, bind, "CLIENT", params.clientId(), "clientId");
        appendRelationFilter(where, bind, "DISPUTE", params.disputeId(), "disputeId");
        appendRelationFilter(where, bind, "TASK", params.taskId(), "taskId");
        appendRelationFilter(where, bind, "DOCUMENT", params.documentId(), "documentId");
    }

    private static void appendRelationFilter(
            StringBuilder where,
            Map<String, Object> bind,
            String relationType,
            UUID relationId,
            String paramName) {
        if (relationId == null) {
            return;
        }
        where.append("""
                 AND EXISTS (
                   SELECT 1 FROM ocr_relations relf
                   WHERE relf.ocr_record_id = r.id
                     AND relf.relation_type = :""")
                .append(paramName).append("Type")
                .append("""
                     AND relf.relation_id = :""")
                .append(paramName)
                .append("""
                 )
                """);
        bind.put(paramName + "Type", relationType);
        bind.put(paramName, relationId);
    }

    private static String sanitizeQuery(String raw) {
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return trimmed;
        }
        return trimmed.replaceAll("[^\\p{L}\\p{N}\\s]", " ").replaceAll("\\s+", " ").trim();
    }

    private static String resolveOrderBy(Pageable pageable) {
        Sort sort = pageable.getSort();
        if (sort.isUnsorted()) {
            return " ORDER BY r.created_at DESC";
        }
        Sort.Order order = sort.iterator().next();
        String column = SORT_COLUMNS.getOrDefault(order.getProperty(), "r.created_at");
        String direction = order.isAscending() ? "ASC" : "DESC";
        return " ORDER BY " + column + " " + direction;
    }
}
