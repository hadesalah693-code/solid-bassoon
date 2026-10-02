package com.kalima.backend.domain.tenant;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.hibernate.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Activates Hibernate's {@code tenantFilter} at the start of every transactional
 * service method, and disables it on exit.
 *
 * <p>Runs <em>inside</em> the Spring transaction advice so it enables the
 * filter on the same {@code Session} that Spring Data will later use for its
 * queries. This is enforced by {@code @EnableTransactionManagement(order =
 * Ordered.HIGHEST_PRECEDENCE + 100)} (see {@code KalimaBackendApplication}),
 * which makes the TX aspect outer and this aspect inner.
 *
 * <p>If {@link TenantContext} has no value (e.g. login flow, health checks),
 * the filter is left untouched so the call sees the global namespace —
 * required for {@code findByEmail} during authentication.
 *
 * <p>Writes are protected by a separate defensive {@code @PrePersist} hook on
 * every tenant-scoped entity; Hibernate filters are read-only.
 */
@Aspect
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class TenantFilterAspect {

    private static final Logger log = LoggerFactory.getLogger(TenantFilterAspect.class);
    private static final String FILTER_NAME = "tenantFilter";
    private static final String FILTER_PARAM = "companyId";

    @PersistenceContext
    private EntityManager entityManager;

    @Around("execution(* com.kalima.backend..service..*(..))")
    public Object withTenantFilter(ProceedingJoinPoint pjp) throws Throwable {
        UUID tenantId = TenantContext.get().orElse(null);
        if (tenantId == null) {
            return pjp.proceed();
        }

        Session session;
        try {
            session = entityManager.unwrap(Session.class);
        } catch (Exception e) {
            throw new IllegalStateException(
                    "[tenant] Cannot activate tenant filter: failed to unwrap Hibernate Session. " +
                    "Refusing to proceed to prevent cross-tenant data exposure.", e);
        }

        session.enableFilter(FILTER_NAME).setParameter(FILTER_PARAM, tenantId);
        try {
            return pjp.proceed();
        } finally {
            try {
                session.disableFilter(FILTER_NAME);
            } catch (Exception ignored) {
                // Session may already be closed — swallow so we don't mask the real exception.
            }
        }
    }
}
