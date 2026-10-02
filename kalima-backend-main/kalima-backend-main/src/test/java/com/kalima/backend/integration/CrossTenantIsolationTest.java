package com.kalima.backend.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kalima.backend.domain.entity.DomainEnums;
import com.kalima.backend.domain.entity.Person;
import com.kalima.backend.domain.repository.CompanyRepository;
import com.kalima.backend.domain.repository.PersonRepository;
import com.kalima.backend.domain.tenant.TenantContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Cross-tenant isolation: proves that Tenant 1 cannot read, enumerate, or act
 * on data that belongs to Tenant 2, and vice-versa.
 *
 * <p>Uses the two pre-seeded companies / admins from {@link KalimaTestDataSeeder}:
 * <ul>
 *   <li>Tenant 1 (default company) — {@code ADMIN_EMAIL}</li>
 *   <li>Tenant 2 ({@code SECOND_COMPANY_ID}) — {@code SECOND_ADMIN_EMAIL}</li>
 * </ul>
 *
 * <p>No production code is touched. All assertions are negative: a 404 proves
 * the Hibernate tenant filter blocked access; a 200 with the foreign id absent
 * from the list proves the filter suppresses enumeration.
 */
@DisplayName("Cross-tenant isolation — disputes, cases, persons")
class CrossTenantIsolationTest extends AbstractPostgresSpringBootTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private PersonRepository personRepository;
    @Autowired
    private CompanyRepository companyRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    // ------------------------------------------------------------------ //
    //  Dispute isolation                                                   //
    // ------------------------------------------------------------------ //

    @Test
    @DisplayName("Tenant 2 dispute is invisible to Tenant 1 GET by id → 404")
    void disputeGetById_crossTenant_returns404() throws Exception {
        String t2Token = loginTenant2Admin();
        String disputeId = createDisputeForTenant2(t2Token);

        String t1Token = loginTenant1Admin();
        mockMvc.perform(get("/api/disputes/" + disputeId)
                        .header("Authorization", "Bearer " + t1Token))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Tenant 2 dispute id does not appear in Tenant 1 dispute list")
    void disputeList_crossTenant_doesNotLeakForeignId() throws Exception {
        String t2Token = loginTenant2Admin();
        String disputeId = createDisputeForTenant2(t2Token);

        String t1Token = loginTenant1Admin();
        mockMvc.perform(get("/api/disputes")
                        .header("Authorization", "Bearer " + t1Token))
                .andExpect(status().isOk())
                // none of the items in the page content array should carry the foreign id
                .andExpect(jsonPath("$.content[*].id", not(hasItem(disputeId))));
    }

    @Test
    @DisplayName("Tenant 1 dispute is invisible to Tenant 2 GET by id → 404")
    void disputeGetById_reverseCrossTenant_returns404() throws Exception {
        String t1Token = loginTenant1Admin();
        String disputeId = createDisputeForTenant1(t1Token);

        String t2Token = loginTenant2Admin();
        mockMvc.perform(get("/api/disputes/" + disputeId)
                        .header("Authorization", "Bearer " + t2Token))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Tenant 2 cannot access stages of a Tenant 1 dispute → 404")
    void disputeStages_crossTenant_returns404() throws Exception {
        String t1Token = loginTenant1Admin();
        String disputeId = createDisputeForTenant1(t1Token);

        String t2Token = loginTenant2Admin();
        mockMvc.perform(get("/api/disputes/" + disputeId + "/stages")
                        .header("Authorization", "Bearer " + t2Token))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Tenant 2 cannot access timeline of a Tenant 1 dispute → 404")
    void disputeTimeline_crossTenant_returns404() throws Exception {
        String t1Token = loginTenant1Admin();
        String disputeId = createDisputeForTenant1(t1Token);

        String t2Token = loginTenant2Admin();
        // GET /api/disputes/{id} contains the timeline; a 404 also covers it,
        // but we verify the updates sub-resource directly too.
        mockMvc.perform(get("/api/disputes/" + disputeId + "/updates")
                        .header("Authorization", "Bearer " + t2Token))
                .andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------------ //
    //  Person isolation                                                    //
    // ------------------------------------------------------------------ //

    @Test
    @DisplayName("Tenant 2 person is invisible to Tenant 1 GET by id → 404")
    void personGetById_crossTenant_returns404() throws Exception {
        UUID t2PersonId = requirePersonId(KalimaTestDataSeeder.SECOND_ADMIN_EMAIL);

        String t1Token = loginTenant1Admin();
        mockMvc.perform(get("/api/persons/" + t2PersonId)
                        .header("Authorization", "Bearer " + t1Token))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Tenant 2 person id does not appear in Tenant 1 person list")
    void personList_crossTenant_doesNotLeakForeignId() throws Exception {
        UUID t2PersonId = requirePersonId(KalimaTestDataSeeder.SECOND_ADMIN_EMAIL);

        String t1Token = loginTenant1Admin();
        mockMvc.perform(get("/api/persons")
                        .header("Authorization", "Bearer " + t1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].id", not(hasItem(t2PersonId.toString()))));
    }

    // ------------------------------------------------------------------ //
    //  Case isolation                                                      //
    // ------------------------------------------------------------------ //

    @Test
    @DisplayName("Tenant 1 case is invisible to Tenant 2 GET by id → 404")
    void caseGetById_crossTenant_returns404() throws Exception {
        String t1Token = loginTenant1Admin();
        String caseId = createCaseForTenant1(t1Token);

        String t2Token = loginTenant2Admin();
        mockMvc.perform(get("/api/cases/" + caseId)
                        .header("Authorization", "Bearer " + t2Token))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Tenant 1 case id does not appear in Tenant 2 case list")
    void caseList_crossTenant_doesNotLeakForeignId() throws Exception {
        String t1Token = loginTenant1Admin();
        String caseId = createCaseForTenant1(t1Token);

        String t2Token = loginTenant2Admin();
        mockMvc.perform(get("/api/cases")
                        .header("Authorization", "Bearer " + t2Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].id", not(hasItem(caseId))));
    }

    // ------------------------------------------------------------------ //
    //  Delegation office attorney isolation                                //
    // ------------------------------------------------------------------ //

    @Test
    @DisplayName("Tenant 2 office attorney id does not appear in Tenant 1's attorney list")
    void officeAttorneyList_crossTenant_doesNotLeakForeignId() throws Exception {
        String t2Token = loginTenant2Admin();
        String attorneyId = createOfficeAttorneyForTenant2(t2Token);

        String t1Token = loginTenant1Admin();
        mockMvc.perform(get("/api/delegations/settings/attorneys")
                        .header("Authorization", "Bearer " + t1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", not(hasItem(attorneyId))));
    }

    @Test
    @DisplayName("Tenant 1 cannot patch a Tenant 2 office attorney → 404")
    void officeAttorneyUpdate_crossTenant_returns404() throws Exception {
        String t2Token = loginTenant2Admin();
        String attorneyId = createOfficeAttorneyForTenant2(t2Token);

        String t1Token = loginTenant1Admin();
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .patch("/api/delegations/settings/attorneys/" + attorneyId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + t1Token)
                        .content("{\"active\": false}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Tenant 1 cannot delete a Tenant 2 office attorney → 404")
    void officeAttorneyDelete_crossTenant_returns404() throws Exception {
        String t2Token = loginTenant2Admin();
        String attorneyId = createOfficeAttorneyForTenant2(t2Token);

        String t1Token = loginTenant1Admin();
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/api/delegations/settings/attorneys/" + attorneyId)
                        .header("Authorization", "Bearer " + t1Token))
                .andExpect(status().isNotFound());
    }

    /**
     * Registers a fresh Tenant-2 staff member as an office attorney. Uses a
     * brand-new person per call (rather than reusing {@code SECOND_ADMIN_EMAIL})
     * so the three isolation tests below don't collide on the
     * "one attorney row per person" uniqueness constraint.
     */
    private String createOfficeAttorneyForTenant2(String t2Token) throws Exception {
        UUID t2StaffId = createExtraTenant2Staff();
        MvcResult res = mockMvc.perform(post("/api/delegations/settings/attorneys")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + t2Token)
                        .content("{\"personId\": \"" + t2StaffId + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString()).get("id").asText();
    }

    private UUID createExtraTenant2Staff() {
        TenantContext.set(KalimaTestDataSeeder.SECOND_COMPANY_ID);
        try {
            Person p = new Person();
            p.setCompany(companyRepository.getReferenceById(KalimaTestDataSeeder.SECOND_COMPANY_ID));
            p.setPersonType(DomainEnums.PersonType.INDIVIDUAL);
            p.setSystemRole(DomainEnums.SystemRole.LAWYER);
            p.setFullName("Cross Tenant Attorney " + UUID.randomUUID());
            p.setEmail("cross-tenant-attorney-" + UUID.randomUUID() + "@kalima.test");
            p.setPasswordHash(passwordEncoder.encode("UnusedCrossTenantPassword123456"));
            p.setActive(true);
            return personRepository.save(p).getId();
        } finally {
            TenantContext.clear();
        }
    }

    // ------------------------------------------------------------------ //
    //  Helpers                                                             //
    // ------------------------------------------------------------------ //

    private String loginTenant1Admin() throws Exception {
        return obtainToken(KalimaTestDataSeeder.ADMIN_EMAIL, KalimaTestDataSeeder.PLAIN_PASSWORD);
    }

    private String loginTenant2Admin() throws Exception {
        return obtainToken(KalimaTestDataSeeder.SECOND_ADMIN_EMAIL, KalimaTestDataSeeder.PLAIN_PASSWORD);
    }

    private String createDisputeForTenant1(String t1Token) throws Exception {
        UUID clientId = requirePersonId(KalimaTestDataSeeder.CLIENT_EMAIL);
        UUID lawyerId = requirePersonId(KalimaTestDataSeeder.LAWYER_EMAIL);
        return createDispute(t1Token, clientId, lawyerId, "Cross-tenant T1 dispute");
    }

    /**
     * Creates a dispute inside Tenant 2.
     * Tenant 2 has an admin but no pre-seeded CLIENT person, so we use the admin's
     * own id as the client placeholder — the API only checks the person exists in the
     * same company and has role CLIENT.  Because the seeder gives SECOND_ADMIN_EMAIL the
     * role ADMIN we cannot use them directly as client.  Instead we rely on the
     * FOREIGN_CLIENT_EMAIL person which is seeded in Tenant 2 with role CLIENT.
     *
     * <p>Actually KalimaTestDataSeeder seeds a CLIENT in SECOND_COMPANY via
     * {@code seedForeignTenantClient()}, but that person has no password so
     * cannot log in.  For creating the dispute we only need the admin token —
     * the clientId just has to be a CLIENT in the same company.
     */
    private String createDisputeForTenant2(String t2Token) throws Exception {
        UUID clientId = requirePersonId(KalimaTestDataSeeder.FOREIGN_CLIENT_EMAIL);
        // Tenant 2 has no lawyer seed; pass null (assignedLawyerId is optional)
        return createDispute(t2Token, clientId, null, "Cross-tenant T2 dispute");
    }

    private String createDispute(String token, UUID clientId, UUID lawyerId, String title)
            throws Exception {
        String lawyerJson = lawyerId == null ? "null" : "\"" + lawyerId + "\"";
        String body = """
                {
                  "title": "%s",
                  "opponentName": "خصم عابر الحدود",
                  "description": null,
                  "disputeType": "CIVIL",
                  "riskLevel": "LOW",
                  "legalPath": null,
                  "claimAmount": null,
                  "successProbability": null,
                  "clientId": "%s",
                  "assignedLawyerId": %s
                }
                """.formatted(title + " " + UUID.randomUUID(), clientId, lawyerJson);
        MvcResult res = mockMvc.perform(post("/api/disputes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString()).get("id").asText();
    }

    private String createCaseForTenant1(String t1Token) throws Exception {
        UUID clientId = requirePersonId(KalimaTestDataSeeder.CLIENT_EMAIL);
        UUID lawyerId = requirePersonId(KalimaTestDataSeeder.LAWYER_EMAIL);
        String caseNumber = "CT-ISO-" + UUID.randomUUID();
        String body = """
                {
                  "caseNumber": "%s",
                  "title": "Cross-tenant case isolation",
                  "clientId": "%s",
                  "assignedLawyerId": "%s"
                }
                """.formatted(caseNumber, clientId, lawyerId);
        MvcResult res = mockMvc.perform(post("/api/cases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + t1Token)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString()).get("id").asText();
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

    private UUID requirePersonId(String email) {
        return personRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("Missing seeded person: " + email))
                .getId();
    }
}
