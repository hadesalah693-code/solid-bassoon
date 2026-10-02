package com.kalima.backend.auth.seed;

import com.kalima.backend.auth.support.EmailNormalization;
import com.kalima.backend.domain.entity.Company;
import com.kalima.backend.domain.entity.DomainEnums;
import com.kalima.backend.domain.entity.Person;
import com.kalima.backend.domain.repository.CompanyRepository;
import com.kalima.backend.domain.repository.PersonRepository;
import com.kalima.backend.domain.tenant.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Optional;

/**
 * Optional production bootstrap: create one ADMIN when {@code INIT_ADMIN_*}
 * env vars are set. Idempotent if the email already exists.
 *
 * <p>{@link AdminSeeder} remains {@code @Profile("dev")} only.</p>
 */
@Component
@Profile("prod")
public class InitialAdminSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(InitialAdminSeeder.class);

    private static final String DEFAULT_ADMIN_NAME = "مدير النظام";

    private final PersonRepository personRepository;
    private final CompanyRepository companyRepository;
    private final PasswordEncoder passwordEncoder;

    private final boolean initAdminEnabled;
    private final boolean initAdminResetPassword;
    private final String initAdminEmailRaw;
    private final String initAdminPassword;
    private final String initAdminName;

    public InitialAdminSeeder(
            PersonRepository personRepository,
            CompanyRepository companyRepository,
            PasswordEncoder passwordEncoder,
            @Value("${INIT_ADMIN_ENABLED:false}") String initAdminEnabled,
            @Value("${INIT_ADMIN_RESET_PASSWORD:false}") String initAdminResetPassword,
            @Value("${INIT_ADMIN_EMAIL:admin@kalima.com}") String initAdminEmail,
            @Value("${INIT_ADMIN_PASSWORD:KalimaAdmin2026!}") String initAdminPassword,
            @Value("${INIT_ADMIN_NAME:مدير النظام}") String initAdminName) {
        this.personRepository = personRepository;
        this.companyRepository = companyRepository;
        this.passwordEncoder = passwordEncoder;
        this.initAdminEnabled = "true".equalsIgnoreCase(initAdminEnabled.trim());
        this.initAdminResetPassword = "true".equalsIgnoreCase(initAdminResetPassword.trim());
        this.initAdminEmailRaw = initAdminEmail;
        this.initAdminPassword = initAdminPassword;
        this.initAdminName = initAdminName;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (!initAdminEnabled) {
            return;
        }
        if (!StringUtils.hasText(initAdminEmailRaw) || !StringUtils.hasText(initAdminPassword)) {
            log.warn(
                    "[init-admin] INIT_ADMIN_ENABLED=true but INIT_ADMIN_EMAIL or INIT_ADMIN_PASSWORD "
                            + "is missing; skipping.");
            return;
        }

        String normalizedEmail = EmailNormalization.normalize(initAdminEmailRaw);
        if (normalizedEmail == null) {
            log.warn("[init-admin] INIT_ADMIN_EMAIL is invalid after normalization; skipping.");
            return;
        }

        TenantContext.set(Company.DEFAULT_ID);
        try {
            Optional<Person> existing = personRepository.findByEmail(normalizedEmail);
            if (existing.isPresent()) {
                if (initAdminResetPassword) {
                    Person admin = existing.get();
                    admin.setPasswordHash(passwordEncoder.encode(initAdminPassword));
                    admin.setActive(true);
                    admin.setArchivedAt(null);
                    if (StringUtils.hasText(initAdminName.trim())) {
                        admin.setFullName(initAdminName.trim());
                    }
                    personRepository.save(admin);
                    log.warn(
                            "[init-admin] Reset password for existing ADMIN {} (active={}, archived={})",
                            maskEmail(normalizedEmail),
                            admin.isActive(),
                            admin.getArchivedAt() != null);
                } else {
                    log.warn(
                            "[init-admin] Skipped password reset for existing ADMIN {} — set INIT_ADMIN_RESET_PASSWORD=true to sync",
                            maskEmail(normalizedEmail));
                }
                return;
            }

            Company company = companyRepository.findById(Company.DEFAULT_ID)
                    .orElseThrow(() -> new IllegalStateException(
                            "Default company " + Company.DEFAULT_ID
                                    + " not found — run Flyway migrations first."));

            String displayName =
                    StringUtils.hasText(initAdminName.trim()) ? initAdminName.trim() : DEFAULT_ADMIN_NAME;

            Person admin = new Person();
            admin.setCompany(company);
            admin.setPersonType(DomainEnums.PersonType.INDIVIDUAL);
            admin.setSystemRole(DomainEnums.SystemRole.ADMIN);
            admin.setFullName(displayName);
            admin.setEmail(normalizedEmail);
            admin.setPasswordHash(passwordEncoder.encode(initAdminPassword));
            admin.setActive(true);

            personRepository.save(admin);

            log.info("[init-admin] Created initial ADMIN for email {}", maskEmail(normalizedEmail));
        } finally {
            TenantContext.clear();
        }
    }

    /** Masks local-part. Password is never logged. */
    static String maskEmail(String email) {
        if (email == null || email.isEmpty()) {
            return "(empty)";
        }
        int at = email.indexOf('@');
        if (at <= 0) {
            return "***";
        }
        String local = email.substring(0, at);
        String domain = email.substring(at);
        if (local.length() <= 1) {
            return "*" + domain;
        }
        return local.charAt(0) + "***" + domain;
    }
}
