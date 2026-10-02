package com.kalima.backend.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kalima.backend.delegation.entity.Delegation;
import com.kalima.backend.delegation.repository.DelegationRepository;
import com.kalima.backend.domain.entity.Company;
import com.kalima.backend.domain.entity.DomainEnums;
import com.kalima.backend.domain.entity.Person;
import com.kalima.backend.domain.repository.CompanyRepository;
import com.kalima.backend.domain.repository.PersonRepository;
import com.kalima.backend.domain.tenant.TenantContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.util.Locale;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 1 core data model additions: optional expiry, notary number, partners,
 * cancellation dates, and document type classification.
 */
@DisplayName("Delegations (POA) module — Phase 1 core data model")
class DelegationIntegrationTest extends AbstractPostgresSpringBootTest {

    @Autowired
    private org.springframework.test.web.servlet.MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private PersonRepository personRepository;
    @Autowired
    private CompanyRepository companyRepository;
    @Autowired
    private DelegationRepository delegationRepository;

    @Test
    void create_withoutExpiry_staysActive_and_persists_notaryNumber_and_partners() throws Exception {
        String token = obtainToken(KalimaTestDataSeeder.ADMIN_EMAIL, KalimaTestDataSeeder.PLAIN_PASSWORD);
        UUID clientId = requirePersonId(KalimaTestDataSeeder.CLIENT_EMAIL);
        UUID partnerId = createExtraClient("Delegation Partner One");

        String body = """
                {
                  "clientId": "%s",
                  "delegationType": "GENERAL",
                  "issuingCountry": "QA",
                  "issuedAt": "%s",
                  "notaryNumber": "2026/123456",
                  "partnerIds": ["%s"]
                }
                """.formatted(clientId, LocalDate.now(), partnerId);

        mockMvc.perform(post("/api/delegations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.expiresAt").value(nullValue()))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.notaryNumber").value("2026/123456"))
                .andExpect(jsonPath("$.partners", hasSize(1)))
                .andExpect(jsonPath("$.partners[0].id").value(partnerId.toString()));
    }

    @Test
    void cancel_records_dates_and_clears_them_on_revert() throws Exception {
        String token = obtainToken(KalimaTestDataSeeder.ADMIN_EMAIL, KalimaTestDataSeeder.PLAIN_PASSWORD);
        UUID clientId = requirePersonId(KalimaTestDataSeeder.CLIENT_EMAIL);

        String createBody = """
                {
                  "clientId": "%s",
                  "delegationType": "GENERAL",
                  "issuingCountry": "QA",
                  "issuedAt": "%s",
                  "expiresAt": "%s"
                }
                """.formatted(clientId, LocalDate.now(), LocalDate.now().plusYears(1));
        MvcResult createRes = mockMvc.perform(post("/api/delegations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(createBody))
                .andExpect(status().isCreated())
                .andReturn();
        String id = objectMapper.readTree(createRes.getResponse().getContentAsString()).get("id").asText();

        LocalDate cancelledAt = LocalDate.now().minusDays(2);
        LocalDate announcedAt = LocalDate.now().minusDays(1);
        String cancelBody = """
                {
                  "status": "CANCELLED",
                  "cancelReason": "BY_PRINCIPAL",
                  "cancelReasonNote": "تم الإلغاء من طرف الموكل",
                  "cancelledAt": "%s",
                  "cancellationAnnouncementAt": "%s"
                }
                """.formatted(cancelledAt, announcedAt);

        mockMvc.perform(patch("/api/delegations/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(cancelBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.cancelledAt").value(cancelledAt.toString()))
                .andExpect(jsonPath("$.cancellationAnnouncementAt").value(announcedAt.toString()));

        mockMvc.perform(patch("/api/delegations/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("{\"status\": \"ACTIVE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.cancelledAt").value(nullValue()))
                .andExpect(jsonPath("$.cancellationAnnouncementAt").value(nullValue()));
    }

    @Test
    void uploadOriginalDocument_generatesDisplayFilename_fromClientAndNotaryNumber() throws Exception {
        String token = obtainToken(KalimaTestDataSeeder.ADMIN_EMAIL, KalimaTestDataSeeder.PLAIN_PASSWORD);
        UUID clientId = requirePersonId(KalimaTestDataSeeder.CLIENT_EMAIL);

        String createBody = """
                {
                  "clientId": "%s",
                  "delegationType": "GENERAL",
                  "issuingCountry": "QA",
                  "issuedAt": "%s",
                  "notaryNumber": "2026/123456"
                }
                """.formatted(clientId, LocalDate.now());
        MvcResult createRes = mockMvc.perform(post("/api/delegations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(createBody))
                .andExpect(status().isCreated())
                .andReturn();
        String id = objectMapper.readTree(createRes.getResponse().getContentAsString()).get("id").asText();

        MockMultipartFile original = new MockMultipartFile(
                "file", "poa-scan.pdf", "application/pdf", "dummy-pdf-bytes".getBytes());
        mockMvc.perform(multipart("/api/delegations/" + id + "/documents")
                        .file(original)
                        .param("documentType", "ORIGINAL")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.documentType").value("ORIGINAL"))
                .andExpect(jsonPath("$.originalFilename").value("Hardening Client - 2026_123456.pdf"));

        MockMultipartFile other = new MockMultipartFile(
                "file", "misc-note.pdf", "application/pdf", "dummy-note-bytes".getBytes());
        mockMvc.perform(multipart("/api/delegations/" + id + "/documents")
                        .file(other)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.documentType").value("OTHER"))
                .andExpect(jsonPath("$.originalFilename").value("misc-note.pdf"));
    }

    @Test
    void search_expiredFilter_matchesPastExpiry_evenWhenStoredStatusIsActive() throws Exception {
        String token = obtainToken(KalimaTestDataSeeder.ADMIN_EMAIL, KalimaTestDataSeeder.PLAIN_PASSWORD);
        UUID clientId = requirePersonId(KalimaTestDataSeeder.CLIENT_EMAIL);
        LocalDate pastExpiry = LocalDate.now().minusDays(10);

        String createBody = """
                {
                  "clientId": "%s",
                  "delegationType": "GENERAL",
                  "issuingCountry": "QA",
                  "issuedAt": "%s",
                  "expiresAt": "%s"
                }
                """.formatted(clientId, pastExpiry.minusYears(1), pastExpiry.plusYears(1));
        MvcResult createRes = mockMvc.perform(post("/api/delegations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(createBody))
                .andExpect(status().isCreated())
                .andReturn();
        String id = objectMapper.readTree(createRes.getResponse().getContentAsString()).get("id").asText();

        TenantContext.set(Company.DEFAULT_ID);
        try {
            Delegation stale = delegationRepository.findScopedById(UUID.fromString(id))
                    .orElseThrow();
            stale.setExpiresAt(pastExpiry);
            stale.setStatus(DomainEnums.DelegationStatus.ACTIVE);
            delegationRepository.save(stale);
        } finally {
            TenantContext.clear();
        }

        mockMvc.perform(get("/api/delegations")
                        .param("status", "EXPIRED")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.id == '" + id + "')]").exists())
                .andExpect(jsonPath("$.content[?(@.id == '" + id + "')].status").value("EXPIRED"));

        mockMvc.perform(get("/api/delegations")
                        .param("status", "ACTIVE")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.id == '" + id + "')]").doesNotExist());
    }

    @Test
    void create_withMultipleAgents_persists_all_and_primary_agent() throws Exception {
        String token = obtainToken(KalimaTestDataSeeder.ADMIN_EMAIL, KalimaTestDataSeeder.PLAIN_PASSWORD);
        UUID clientId = requirePersonId(KalimaTestDataSeeder.CLIENT_EMAIL);
        UUID lawyerId = requirePersonId(KalimaTestDataSeeder.LAWYER_EMAIL);
        UUID secondAgentId = requirePersonId(KalimaTestDataSeeder.ADMIN_EMAIL);

        String body = """
                {
                  "clientId": "%s",
                  "agentIds": ["%s", "%s"],
                  "delegationType": "GENERAL",
                  "issuingCountry": "QA",
                  "issuedAt": "%s"
                }
                """.formatted(clientId, lawyerId, secondAgentId, LocalDate.now());

        mockMvc.perform(post("/api/delegations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.agents", hasSize(2)))
                .andExpect(jsonPath("$.agent.id").value(lawyerId.toString()));
    }

    @Test
    void create_withNoteMention_notifies_mentioned_staff() throws Exception {
        String adminToken = obtainToken(KalimaTestDataSeeder.ADMIN_EMAIL, KalimaTestDataSeeder.PLAIN_PASSWORD);
        String lawyerToken = obtainToken(KalimaTestDataSeeder.LAWYER_EMAIL, KalimaTestDataSeeder.PLAIN_PASSWORD);
        UUID clientId = requirePersonId(KalimaTestDataSeeder.CLIENT_EMAIL);

        String body = """
                {
                  "clientId": "%s",
                  "delegationType": "GENERAL",
                  "issuingCountry": "QA",
                  "issuedAt": "%s",
                  "notes": "يرجى المراجعة @Hardening Lawyer "
                }
                """.formatted(clientId, LocalDate.now());

        mockMvc.perform(post("/api/delegations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + adminToken)
                        .content(body))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/notifications")
                        .header("Authorization", "Bearer " + lawyerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.type=='DELEGATION_MENTION')]").exists())
                .andExpect(jsonPath("$.content[?(@.title=='إشارة في توكيل')]").exists());
    }

    @Test
    void create_withReminderRecipient_notifies_recipient() throws Exception {
        String adminToken = obtainToken(KalimaTestDataSeeder.ADMIN_EMAIL, KalimaTestDataSeeder.PLAIN_PASSWORD);
        String lawyerToken = obtainToken(KalimaTestDataSeeder.LAWYER_EMAIL, KalimaTestDataSeeder.PLAIN_PASSWORD);
        UUID clientId = requirePersonId(KalimaTestDataSeeder.CLIENT_EMAIL);
        UUID lawyerId = requirePersonId(KalimaTestDataSeeder.LAWYER_EMAIL);

        String body = """
                {
                  "clientId": "%s",
                  "delegationType": "GENERAL",
                  "issuingCountry": "QA",
                  "issuedAt": "%s",
                  "expiresAt": "%s",
                  "reminderOffsets": [30],
                  "reminderRecipientIds": ["%s"]
                }
                """.formatted(clientId, LocalDate.now(), LocalDate.now().plusYears(1), lawyerId);

        mockMvc.perform(post("/api/delegations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + adminToken)
                        .content(body))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/notifications")
                        .header("Authorization", "Bearer " + lawyerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.type=='DELEGATION_REMINDER_ADDED')]").exists())
                .andExpect(jsonPath("$.content[?(@.title=='تذكيرات توكيل')]").exists());
    }

    private UUID createExtraClient(String fullName) {
        TenantContext.set(Company.DEFAULT_ID);
        try {
            Person p = new Person();
            p.setCompany(companyRepository.getReferenceById(Company.DEFAULT_ID));
            p.setPersonType(DomainEnums.PersonType.INDIVIDUAL);
            p.setSystemRole(DomainEnums.SystemRole.CLIENT);
            p.setFullName(fullName);
            p.setEmail(fullName.toLowerCase(Locale.ROOT).replace(" ", "-") + "@kalima.test");
            p.setActive(true);
            return personRepository.save(p).getId();
        } finally {
            TenantContext.clear();
        }
    }

    private UUID requirePersonId(String email) {
        return personRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("Missing seeded person: " + email))
                .getId();
    }

    private String obtainToken(String email, String password) throws Exception {
        String r = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(r).get("accessToken").asText();
    }
}
