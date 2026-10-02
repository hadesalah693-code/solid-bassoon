package com.kalima.backend.integration;

import com.kalima.backend.domain.entity.Company;
import com.kalima.backend.domain.entity.DomainEnums;
import com.kalima.backend.domain.entity.Person;
import com.kalima.backend.domain.repository.CompanyRepository;
import com.kalima.backend.domain.repository.PersonRepository;
import com.kalima.backend.domain.tenant.TenantContext;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Idempotent test data: default-tenant users + a client row in a second
 * company for cross-tenant 404 tests. Runs only in {@code test} profile.
 */
@Component
@Profile("test")
@Order(1)
public class KalimaTestDataSeeder implements ApplicationRunner {

    public static final String ADMIN_EMAIL = "hardening-admin@kalima.test";
    public static final String LAWYER_EMAIL = "hardening-lawyer@kalima.test";
    public static final String CLIENT_EMAIL = "hardening-client@kalima.test";
    public static final String WITNESS_EMAIL = "hardening-witness@kalima.test";
    public static final String FOREIGN_CLIENT_EMAIL = "hardening-foreign@kalima.test";
    /** Admin in {@link #SECOND_COMPANY_ID} for multi-tenant API tests (e.g. dispute numbering). */
    public static final String SECOND_ADMIN_EMAIL = "hardening-second-admin@kalima.test";
    /**
     * Admin used only in tests that mutate {@code system_role} in the DB; never
     * use for other hardening cases so parallel runs stay isolated.
     */
    public static final String MUTABLE_ADMIN_EMAIL = "hardening-mutable-admin@kalima.test";
    public static final String MUTABLE_LAWYER_EMAIL = "hardening-mutable-lawyer@kalima.test";
    public static final String PLAIN_PASSWORD = "TestPassword123456";
    public static final UUID SECOND_COMPANY_ID =
            UUID.fromString("5b4bbf78-abef-4483-aad4-5ef81cc7ba9e");

    private final PersonRepository personRepository;
    private final CompanyRepository companyRepository;
    private final PasswordEncoder passwordEncoder;

    public KalimaTestDataSeeder(
            PersonRepository personRepository,
            CompanyRepository companyRepository,
            PasswordEncoder passwordEncoder) {
        this.personRepository = personRepository;
        this.companyRepository = companyRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        ensureSecondCompany();
        ensureSecondTenantAdmin();
        seedDefaultTenantUsers();
        seedForeignTenantClient();
    }

    private void ensureSecondTenantAdmin() {
        if (personRepository.findByEmail(SECOND_ADMIN_EMAIL).isPresent()) {
            return;
        }
        TenantContext.set(SECOND_COMPANY_ID);
        try {
            Person p = new Person();
            p.setCompany(companyRepository.getReferenceById(SECOND_COMPANY_ID));
            p.setPersonType(DomainEnums.PersonType.INDIVIDUAL);
            p.setSystemRole(DomainEnums.SystemRole.ADMIN);
            p.setFullName("Hardening Second Tenant Admin");
            p.setEmail(SECOND_ADMIN_EMAIL);
            p.setPasswordHash(passwordEncoder.encode(PLAIN_PASSWORD));
            p.setActive(true);
            personRepository.save(p);
        } finally {
            TenantContext.clear();
        }
    }

    private void ensureSecondCompany() {
        if (companyRepository.findById(SECOND_COMPANY_ID).isEmpty()) {
            Company c = new Company();
            c.setId(SECOND_COMPANY_ID);
            c.setName("Hardening Tenant Two");
            c.setActive(true);
            companyRepository.save(c);
        }
    }

    private void seedDefaultTenantUsers() {
        TenantContext.set(Company.DEFAULT_ID);
        try {
            ensureAdmin();
            ensureMutableAdmin();
            ensureMutableLawyer();
            ensureLawyer();
            ensureClient();
            ensureWitness();
        } finally {
            TenantContext.clear();
        }
    }

    private void ensureAdmin() {
        if (personRepository.findByEmail(ADMIN_EMAIL).isPresent()) {
            return;
        }
        Person p = new Person();
        p.setCompany(companyRepository.getReferenceById(Company.DEFAULT_ID));
        p.setPersonType(DomainEnums.PersonType.INDIVIDUAL);
        p.setSystemRole(DomainEnums.SystemRole.ADMIN);
        p.setFullName("Hardening Admin");
        p.setEmail(ADMIN_EMAIL);
        p.setPasswordHash(passwordEncoder.encode(PLAIN_PASSWORD));
        p.setActive(true);
        personRepository.save(p);
    }

    private void ensureMutableAdmin() {
        if (personRepository.findByEmail(MUTABLE_ADMIN_EMAIL).isPresent()) {
            return;
        }
        Person p = new Person();
        p.setCompany(companyRepository.getReferenceById(Company.DEFAULT_ID));
        p.setPersonType(DomainEnums.PersonType.INDIVIDUAL);
        p.setSystemRole(DomainEnums.SystemRole.ADMIN);
        p.setFullName("Hardening Mutable Admin");
        p.setEmail(MUTABLE_ADMIN_EMAIL);
        p.setPasswordHash(passwordEncoder.encode(PLAIN_PASSWORD));
        p.setActive(true);
        personRepository.save(p);
    }

    private void ensureMutableLawyer() {
        if (personRepository.findByEmail(MUTABLE_LAWYER_EMAIL).isPresent()) {
            return;
        }
        Person p = new Person();
        p.setCompany(companyRepository.getReferenceById(Company.DEFAULT_ID));
        p.setPersonType(DomainEnums.PersonType.INDIVIDUAL);
        p.setSystemRole(DomainEnums.SystemRole.LAWYER);
        p.setFullName("Hardening Mutable Lawyer");
        p.setEmail(MUTABLE_LAWYER_EMAIL);
        p.setPasswordHash(passwordEncoder.encode(PLAIN_PASSWORD));
        p.setActive(true);
        personRepository.save(p);
    }

    private void ensureLawyer() {
        if (personRepository.findByEmail(LAWYER_EMAIL).isPresent()) {
            return;
        }
        Person p = new Person();
        p.setCompany(companyRepository.getReferenceById(Company.DEFAULT_ID));
        p.setPersonType(DomainEnums.PersonType.INDIVIDUAL);
        p.setSystemRole(DomainEnums.SystemRole.LAWYER);
        p.setFullName("Hardening Lawyer");
        p.setEmail(LAWYER_EMAIL);
        p.setPasswordHash(passwordEncoder.encode(PLAIN_PASSWORD));
        p.setActive(true);
        personRepository.save(p);
    }

    private void ensureClient() {
        if (personRepository.findByEmail(CLIENT_EMAIL).isPresent()) {
            return;
        }
        Person p = new Person();
        p.setCompany(companyRepository.getReferenceById(Company.DEFAULT_ID));
        p.setPersonType(DomainEnums.PersonType.INDIVIDUAL);
        p.setSystemRole(DomainEnums.SystemRole.CLIENT);
        p.setFullName("Hardening Client");
        p.setEmail(CLIENT_EMAIL);
        p.setActive(true);
        personRepository.save(p);
    }

    private void ensureWitness() {
        if (personRepository.findByEmail(WITNESS_EMAIL).isPresent()) {
            return;
        }
        Person p = new Person();
        p.setCompany(companyRepository.getReferenceById(Company.DEFAULT_ID));
        p.setPersonType(DomainEnums.PersonType.INDIVIDUAL);
        p.setSystemRole(DomainEnums.SystemRole.LAWYER);
        p.setFullName("Hardening Witness");
        p.setEmail(WITNESS_EMAIL);
        p.setPasswordHash(passwordEncoder.encode(PLAIN_PASSWORD));
        p.setActive(true);
        personRepository.save(p);
    }

    private void seedForeignTenantClient() {
        if (personRepository.findByEmail(FOREIGN_CLIENT_EMAIL).isPresent()) {
            return;
        }
        TenantContext.set(SECOND_COMPANY_ID);
        try {
            Person p = new Person();
            p.setCompany(companyRepository.getReferenceById(SECOND_COMPANY_ID));
            p.setPersonType(DomainEnums.PersonType.INDIVIDUAL);
            p.setSystemRole(DomainEnums.SystemRole.CLIENT);
            p.setFullName("Foreign Tenant Client");
            p.setEmail(FOREIGN_CLIENT_EMAIL);
            p.setActive(true);
            personRepository.save(p);
        } finally {
            TenantContext.clear();
        }
    }
}
