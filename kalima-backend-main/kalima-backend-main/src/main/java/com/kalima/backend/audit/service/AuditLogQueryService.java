package com.kalima.backend.audit.service;

import com.kalima.backend.audit.dto.AuditLogResponse;
import com.kalima.backend.audit.entity.AuditLog;
import com.kalima.backend.audit.repository.AuditLogRepository;
import com.kalima.backend.audit.repository.AuditLogSpecifications;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Read path for audit logs. Lives in the {@code service} package so that
 * {@code TenantFilterAspect} wraps the call and activates the Hibernate
 * {@code tenantFilter} — otherwise calling the repository directly from the
 * controller would return cross-tenant rows.
 *
 * <p>DTO mapping must stay inside this {@code @Transactional} boundary so
 * access to {@link AuditLog#getCompany()} and {@link AuditLog#getActorPerson()}
 * does not run with a closed persistence context when {@code spring.jpa.open-in-view}
 * is false.
 */
@Service
public class AuditLogQueryService {

    private final AuditLogRepository auditLogRepository;

    public AuditLogQueryService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Transactional(readOnly = true)
    public Page<AuditLogResponse> search(String action,
                                         UUID actorId,
                                         String entityType,
                                         UUID entityId,
                                         LocalDateTime from,
                                         LocalDateTime to,
                                         Boolean success,
                                         String q,
                                         Pageable pageable) {
        return auditLogRepository
                .findAll(
                        AuditLogSpecifications.filters(
                                action, actorId, entityType, entityId, from, to, success, q),
                        pageable)
                .map(this::toResponse);
    }

    private AuditLogResponse toResponse(AuditLog a) {
        return new AuditLogResponse(
                a.getId(),
                a.getCompany() != null ? a.getCompany().getId() : null,
                a.getActorPerson() != null ? a.getActorPerson().getId() : null,
                a.getActorEmail(),
                a.getAction(),
                a.getEntityType(),
                a.getEntityId(),
                a.getIpAddress(),
                a.getUserAgent(),
                a.getMetadata(),
                a.isSuccess(),
                a.getErrorMessage(),
                a.getCreatedAt()
        );
    }
}
