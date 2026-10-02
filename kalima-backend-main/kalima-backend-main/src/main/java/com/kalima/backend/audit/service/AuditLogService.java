package com.kalima.backend.audit.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kalima.backend.audit.entity.AuditLog;
import com.kalima.backend.audit.repository.AuditLogRepository;
import com.kalima.backend.domain.entity.Company;
import com.kalima.backend.domain.entity.Person;
import com.kalima.backend.domain.repository.CompanyRepository;
import com.kalima.backend.domain.tenant.TenantContext;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Map;
import java.util.UUID;

/**
 * Central sink for audit events. Every call uses {@link Propagation#REQUIRES_NEW}
 * so the write survives a rollback of the outer business transaction — a
 * failed login, a constraint violation, etc. still leaves a permanent trail.
 *
 * <p>Failures inside {@code record()} are swallowed and logged at ERROR.
 * Audit logging must never cause a user-facing request to fail.
 */
@Service
public class AuditLogService {

    private static final Logger log = LoggerFactory.getLogger(AuditLogService.class);

    private static final int USER_AGENT_MAX = 500;

    private final AuditLogRepository auditLogRepository;
    private final CompanyRepository companyRepository;
    private final ObjectMapper objectMapper;

    public AuditLogService(AuditLogRepository auditLogRepository,
                           CompanyRepository companyRepository,
                           ObjectMapper objectMapper) {
        this.auditLogRepository = auditLogRepository;
        this.companyRepository = companyRepository;
        this.objectMapper = objectMapper;
    }

    // ---------- High-level helpers used by business services ----------
    //
    // Both helpers are annotated with REQUIRES_NEW directly so that the Spring
    // proxy creates a separate transaction on every call. A prior design
    // delegated to a shared private method, but internal self-invocation
    // bypasses the proxy and the annotation was silently ignored — a failed
    // login would roll back its own audit record.

    /** Successful event, actor taken from the current security context. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordSuccess(String action, Person actor,
                              String entityType, UUID entityId,
                              Map<String, Object> metadata) {
        write(action, actor, null, entityType, entityId, metadata, true, null);
    }

    /** Failed event, actor may be null (pre-auth). */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(String action, Person actor, String actorEmail,
                              String entityType, UUID entityId,
                              Map<String, Object> metadata, String errorMessage) {
        write(action, actor, actorEmail, entityType, entityId, metadata, false, errorMessage);
    }

    // ---------- Core write (called from within the REQUIRES_NEW tx) ----------

    private void write(String action,
                       Person actor,
                       String actorEmailOverride,
                       String entityType,
                       UUID entityId,
                       Map<String, Object> metadata,
                       boolean success,
                       String errorMessage) {
        try {
            AuditLog entry = new AuditLog();

            // Resolve tenant: prefer the actor's company, fall back to
            // TenantContext (populated by the JWT filter on every request).
            Company company = null;
            if (actor != null && actor.getCompany() != null) {
                company = actor.getCompany();
            } else {
                UUID tenantId = TenantContext.get().orElse(null);
                if (tenantId != null) {
                    company = companyRepository.getReferenceById(tenantId);
                }
            }
            entry.setCompany(company);

            entry.setActorPerson(actor);
            entry.setActorEmail(actorEmailOverride != null
                    ? actorEmailOverride
                    : (actor != null ? actor.getEmail() : null));

            entry.setAction(action);
            entry.setEntityType(entityType);
            entry.setEntityId(entityId);
            entry.setMetadata(serialize(metadata));
            entry.setSuccess(success);
            entry.setErrorMessage(truncate(errorMessage, Integer.MAX_VALUE));

            HttpServletRequest req = currentRequest();
            if (req != null) {
                entry.setIpAddress(clientIp(req));
                entry.setUserAgent(truncate(req.getHeader("User-Agent"), USER_AGENT_MAX));
            }

            auditLogRepository.save(entry);
        } catch (Exception ex) {
            // NEVER let audit failures break a business request.
            log.error("Failed to write audit log for action={}, entity={}:{} — {}",
                    action, entityType, entityId, ex.getMessage(), ex);
        }
    }

    // ---------- Helpers ----------

    private String serialize(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (JsonProcessingException e) {
            log.warn("Could not serialise audit metadata: {}", e.getMessage());
            return null;
        }
    }

    private HttpServletRequest currentRequest() {
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        if (attrs instanceof ServletRequestAttributes servletAttrs) {
            return servletAttrs.getRequest();
        }
        return null;
    }

    private String clientIp(HttpServletRequest req) {
        String xff = req.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            // Use the first hop in the chain (client origin).
            int comma = xff.indexOf(',');
            return (comma < 0 ? xff : xff.substring(0, comma)).trim();
        }
        return req.getRemoteAddr();
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
