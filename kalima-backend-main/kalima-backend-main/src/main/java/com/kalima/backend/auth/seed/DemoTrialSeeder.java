package com.kalima.backend.auth.seed;

import com.kalima.backend.casefile.entity.CaseFile;
import com.kalima.backend.casefile.repository.CaseFileRepository;
import com.kalima.backend.caseparticipant.service.CaseParticipantService;
import com.kalima.backend.domain.entity.Company;
import com.kalima.backend.domain.entity.DomainEnums;
import com.kalima.backend.domain.entity.Message;
import com.kalima.backend.domain.entity.Person;
import com.kalima.backend.domain.repository.MessageRepository;
import com.kalima.backend.domain.repository.PersonRepository;
import com.kalima.backend.domain.tenant.TenantContext;
import com.kalima.backend.hearing.entity.Hearing;
import com.kalima.backend.hearing.repository.HearingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Enriches the dev environment with a walkthrough-ready scenario: lawyer,
 * assigned case, participants, sample messages, and an upcoming hearing.
 */
@Component
@Profile("dev")
@Order(2)
public class DemoTrialSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoTrialSeeder.class);

    private final PersonRepository personRepository;
    private final CaseFileRepository caseFileRepository;
    private final CaseParticipantService caseParticipantService;
    private final MessageRepository messageRepository;
    private final HearingRepository hearingRepository;
    private final PasswordEncoder passwordEncoder;

    public DemoTrialSeeder(PersonRepository personRepository,
                           CaseFileRepository caseFileRepository,
                           CaseParticipantService caseParticipantService,
                           MessageRepository messageRepository,
                           HearingRepository hearingRepository,
                           PasswordEncoder passwordEncoder) {
        this.personRepository = personRepository;
        this.caseFileRepository = caseFileRepository;
        this.caseParticipantService = caseParticipantService;
        this.messageRepository = messageRepository;
        this.hearingRepository = hearingRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(String... args) {
        TenantContext.set(Company.DEFAULT_ID);
        try {
            Person admin = requirePerson(DemoTrialAccounts.ADMIN_EMAIL);
            Person client = requirePerson(DemoTrialAccounts.CLIENT_EMAIL);
            Person lawyer = ensureLawyer(admin);
            CaseFile demoCase = ensureDemoCase(admin, client, lawyer);
            ensureParticipants(demoCase, client, lawyer, admin);
            ensureSampleMessages(demoCase, lawyer, client);
            ensureUpcomingHearing(demoCase, admin);
            log.warn("[DEV TRIAL] Ready — admin: {} / {}, lawyer: {} / {}, client: {} / {}",
                    DemoTrialAccounts.ADMIN_EMAIL, DemoTrialAccounts.PASSWORD,
                    DemoTrialAccounts.LAWYER_EMAIL, DemoTrialAccounts.PASSWORD,
                    DemoTrialAccounts.CLIENT_EMAIL, DemoTrialAccounts.CLIENT_PASSWORD);
        } finally {
            TenantContext.clear();
        }
    }

    private Person requirePerson(String email) {
        return personRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException(
                        "Missing seeded person " + email + ". AdminSeeder must run first."));
    }

    private Person ensureLawyer(Person admin) {
        return personRepository.findByEmail(DemoTrialAccounts.LAWYER_EMAIL).orElseGet(() -> {
            Person lawyer = new Person();
            lawyer.setCompany(admin.getCompany());
            lawyer.setPersonType(DomainEnums.PersonType.INDIVIDUAL);
            lawyer.setSystemRole(DomainEnums.SystemRole.LAWYER);
            lawyer.setFullName("محامٍ تجريبي");
            lawyer.setEmail(DemoTrialAccounts.LAWYER_EMAIL);
            lawyer.setPasswordHash(passwordEncoder.encode(DemoTrialAccounts.PASSWORD));
            lawyer.setActive(true);
            Person saved = personRepository.save(lawyer);
            log.warn("[DEV TRIAL] Seeded lawyer: {} / {}",
                    DemoTrialAccounts.LAWYER_EMAIL, DemoTrialAccounts.PASSWORD);
            return saved;
        });
    }

    private CaseFile ensureDemoCase(Person admin, Person client, Person lawyer) {
        CaseFile caseFile = caseFileRepository.findByCaseNumber(DemoTrialAccounts.DEMO_CASE_NUMBER)
                .orElseGet(() -> {
                    CaseFile created = new CaseFile();
                    created.setCompany(admin.getCompany());
                    created.setCaseNumber(DemoTrialAccounts.DEMO_CASE_NUMBER);
                    created.setTitle(DemoTrialAccounts.DEMO_CASE_TITLE);
                    created.setDescription(
                            "ملف تجريبي كامل: رسائل، جلسة قادمة، ومتابعة من بوابة العميل.");
                    created.setCaseType("مدني");
                    created.setCourtName("محكمة القاهرة الابتدائية");
                    created.setCourtCircuit("الدائرة الأولى");
                    created.setProcedureStage("متابعة");
                    created.setStatus(DomainEnums.CaseStatus.IN_PROGRESS);
                    created.setClient(client);
                    created.setCreatedBy(admin);
                    return caseFileRepository.save(created);
                });

        if (caseFile.getAssignedLawyer() == null
                || !caseFile.getAssignedLawyer().getId().equals(lawyer.getId())) {
            caseFile.setAssignedLawyer(lawyer);
            caseFileRepository.save(caseFile);
        }
        return caseFile;
    }

    private void ensureParticipants(CaseFile caseFile,
                                      Person client,
                                      Person lawyer,
                                      Person admin) {
        caseParticipantService.addPrimarySeed(
                caseFile, client, DomainEnums.CaseParticipantRole.CLIENT, admin);
        caseParticipantService.addPrimarySeed(
                caseFile, lawyer, DomainEnums.CaseParticipantRole.ASSIGNED_LAWYER, admin);
    }

    private void ensureSampleMessages(CaseFile caseFile, Person lawyer, Person client) {
        if (!messageRepository.findConversationForStaff(caseFile.getId()).isEmpty()) {
            return;
        }
        saveMessage(caseFile, lawyer, client,
                "مرحباً، تم فتح ملف قضيتكم لدى المكتب. سنبقيكم على اطلاع بكل مستجد.");
        saveMessage(caseFile, client, lawyer,
                "شكراً، متى موعد الجلسة القادمة؟");
        saveMessage(caseFile, lawyer, client,
                "الجلسة القادمة بعد أسبوع. سنوافيكم بالتفاصيل قبل الموعد بـ 48 ساعة.");
        log.warn("[DEV TRIAL] Seeded sample messages on case {}", caseFile.getCaseNumber());
    }

    private void saveMessage(CaseFile caseFile, Person sender, Person receiver, String content) {
        Message message = new Message();
        message.setCompany(caseFile.getCompany());
        message.setCaseFile(caseFile);
        message.setSenderUser(sender);
        message.setReceiverUser(receiver);
        message.setSenderType(DomainEnums.MessageSenderType.USER);
        message.setContent(content);
        messageRepository.save(message);
    }

    private void ensureUpcomingHearing(CaseFile caseFile, Person admin) {
        if (!hearingRepository.findByCaseIdForList(
                caseFile.getId(),
                null,
                null,
                null,
                null,
                null,
                org.springframework.data.domain.Pageable.ofSize(1)).isEmpty()) {
            return;
        }
        Hearing hearing = new Hearing();
        hearing.setCompany(caseFile.getCompany());
        hearing.setCaseFile(caseFile);
        hearing.setHearingDate(java.time.LocalDateTime.now().plusDays(7).withHour(10).withMinute(0));
        hearing.setCourtName(caseFile.getCourtName());
        hearing.setChamber("قاعة 3");
        hearing.setNotes("جلسة تجريبية — متابعة الطلبات.");
        hearing.setCreatedBy(admin);
        hearingRepository.save(hearing);
        log.warn("[DEV TRIAL] Seeded upcoming hearing for case {}", caseFile.getCaseNumber());
    }
}
