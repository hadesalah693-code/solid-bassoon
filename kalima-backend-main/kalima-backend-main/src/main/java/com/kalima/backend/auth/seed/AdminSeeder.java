package com.kalima.backend.auth.seed;

import com.kalima.backend.domain.entity.Company;
import com.kalima.backend.domain.entity.DomainEnums;
import com.kalima.backend.domain.entity.Person;
import com.kalima.backend.casefile.entity.CaseFile;
import com.kalima.backend.casefile.repository.CaseFileRepository;
import com.kalima.backend.domain.repository.CompanyRepository;
import com.kalima.backend.domain.repository.PersonRepository;
import com.kalima.backend.domain.tenant.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * DEV-only seeder: makes the local environment reproducible by ensuring the
 * default tenant + admin, and a second tenant + admin used by multi-tenancy
 * tests. Fully idempotent — safe to run on every startup.
 */
@Component
@Profile("dev")
@Order(1)
public class AdminSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminSeeder.class);

    // Default tenant (company row seeded by Flyway V5).
    private static final String DEFAULT_ADMIN_EMAIL = DemoTrialAccounts.ADMIN_EMAIL;
    private static final String DEFAULT_ADMIN_PASSWORD = DemoTrialAccounts.PASSWORD;
    private static final String DEMO_CLIENT_EMAIL = DemoTrialAccounts.CLIENT_EMAIL;
    private static final String DEMO_CLIENT_PASSWORD = DemoTrialAccounts.CLIENT_PASSWORD;
    private static final String DEMO_CLIENT_NAME = "Demo Client";
    private static final String DEMO_CASE_NUMBER = DemoTrialAccounts.DEMO_CASE_NUMBER;

    // Second tenant for cross-tenant isolation testing. Fixed UUID so E2E
    // scripts and DB snapshots stay stable across resets.
    private static final UUID SECOND_COMPANY_ID =
            UUID.fromString("5b4bbf78-abef-4483-aad4-5ef81cc7ba9e");
    private static final String SECOND_COMPANY_NAME = "Tenant Two";
    private static final String SECOND_ADMIN_EMAIL = "admin2@kalima.local";
    private static final String SECOND_ADMIN_PASSWORD = "admin123";

    private final PersonRepository personRepository;
    private final CompanyRepository companyRepository;
    private final CaseFileRepository caseFileRepository;
    private final PasswordEncoder passwordEncoder;

    public AdminSeeder(PersonRepository personRepository,
                       CompanyRepository companyRepository,
                       CaseFileRepository caseFileRepository,
                       PasswordEncoder passwordEncoder) {
        this.personRepository = personRepository;
        this.companyRepository = companyRepository;
        this.caseFileRepository = caseFileRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(String... args) {
        seedDefaultTenantAdmin();
        seedSecondTenantAndAdmin();
    }

    private void seedDefaultTenantAdmin() {
        TenantContext.set(Company.DEFAULT_ID);
        try {
            Company defaultCompany = companyRepository.findById(Company.DEFAULT_ID)
                    .orElseThrow(() -> new IllegalStateException(
                            "Default company " + Company.DEFAULT_ID + " not found. "
                            + "Migration V5 should have seeded it."));
            Person admin = ensureAdmin(defaultCompany, DEFAULT_ADMIN_EMAIL, DEFAULT_ADMIN_PASSWORD,
                    "System Admin");
            Person client = ensureDemoClient(defaultCompany);
            ensureDemoCase(defaultCompany, client, admin);
        } finally {
            TenantContext.clear();
        }
    }

    private void seedSecondTenantAndAdmin() {
        TenantContext.set(SECOND_COMPANY_ID);
        try {
            Company company = companyRepository.findById(SECOND_COMPANY_ID)
                    .orElseGet(() -> {
                        Company c = new Company();
                        c.setId(SECOND_COMPANY_ID);
                        c.setName(SECOND_COMPANY_NAME);
                        c.setActive(true);
                        Company saved = companyRepository.save(c);
                        log.warn("[DEV] Seeded second tenant company: id={} name={}",
                                saved.getId(), saved.getName());
                        return saved;
                    });
            ensureAdmin(company, SECOND_ADMIN_EMAIL, SECOND_ADMIN_PASSWORD,
                    "Tenant Two Admin");
        } finally {
            TenantContext.clear();
        }
    }

    private Person ensureAdmin(Company company, String email, String password, String fullName) {
        return personRepository.findByEmail(email).orElseGet(() -> {
            Person admin = new Person();
            admin.setCompany(company);
            admin.setPersonType(DomainEnums.PersonType.INDIVIDUAL);
            admin.setSystemRole(DomainEnums.SystemRole.ADMIN);
            admin.setFullName(fullName);
            admin.setEmail(email);
            admin.setPasswordHash(passwordEncoder.encode(password));
            admin.setActive(true);
            Person saved = personRepository.save(admin);
            log.warn("[DEV] Seeded admin: {} / {} (company: {})",
                    email, password, company.getName());
            return saved;
        });
    }

    private Person ensureDemoClient(Company company) {
        return personRepository.findByEmail(DEMO_CLIENT_EMAIL).orElseGet(() -> {
            Person client = new Person();
            client.setCompany(company);
            client.setPersonType(DomainEnums.PersonType.INDIVIDUAL);
            client.setSystemRole(DomainEnums.SystemRole.CLIENT);
            client.setFullName(DEMO_CLIENT_NAME);
            client.setEmail(DEMO_CLIENT_EMAIL);
            client.setPasswordHash(passwordEncoder.encode(DEMO_CLIENT_PASSWORD));
            client.setActive(true);
            Person saved = personRepository.save(client);
            log.warn("[DEV] Seeded client: {} / {} (company: {})",
                    DEMO_CLIENT_EMAIL, DEMO_CLIENT_PASSWORD, company.getName());
            return saved;
        });
    }

    private void ensureDemoCase(Company company, Person client, Person admin) {
        if (caseFileRepository.existsByCaseNumberScoped(DEMO_CASE_NUMBER)) {
            return;
        }
        CaseFile c = new CaseFile();
        c.setCompany(company);
        c.setCaseNumber(DEMO_CASE_NUMBER);
        c.setTitle(DemoTrialAccounts.DEMO_CASE_TITLE);
        c.setDescription("ملف تجريبي لتجربة بوابة العميل ومتابعة القضية.");
        c.setCaseType("مدني");
        c.setCourtName("محكمة القاهرة الابتدائية");
        c.setCourtCircuit("الدائرة الأولى");
        c.setProcedureStage("متابعة");
        c.setStatus(DomainEnums.CaseStatus.IN_PROGRESS);
        c.setClient(client);
        c.setCreatedBy(admin);
        caseFileRepository.save(c);
        log.warn("[DEV] Seeded demo client case: {} (client: {})",
                DEMO_CASE_NUMBER, DEMO_CLIENT_EMAIL);
    }
}
