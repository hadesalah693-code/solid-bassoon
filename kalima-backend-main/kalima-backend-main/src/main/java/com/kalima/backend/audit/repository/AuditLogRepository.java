package com.kalima.backend.audit.repository;

import com.kalima.backend.audit.entity.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.UUID;

/**
 * {@link JpaSpecificationExecutor} is used so dynamic filtering builds the
 * SQL only from the provided criteria. A simple {@code :param IS NULL OR ...}
 * JPQL query would make PostgreSQL fail with "could not determine data type
 * of parameter" for typed parameters (UUID, timestamp, boolean).
 */
public interface AuditLogRepository
        extends JpaRepository<AuditLog, UUID>, JpaSpecificationExecutor<AuditLog> {
}
