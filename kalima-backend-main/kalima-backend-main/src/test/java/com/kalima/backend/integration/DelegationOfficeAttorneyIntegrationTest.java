package com.kalima.backend.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 2: Delegation Settings — office attorneys CRUD, default/active rules,
 * and the OCR name-matching helper.
 */
@DisplayName("Delegation Settings — office attorneys")
class DelegationOfficeAttorneyIntegrationTest extends AbstractPostgresSpringBootTest {

    private static final String BASE = "/api/delegations/settings/attorneys";
    private static final String EXTRA_STAFF_PASSWORD = "ExtraStaffTestPassword123456";

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

    @Test
    void create_staffPerson_succeeds_withDefaults() throws Exception {
        String token = obtainToken(KalimaTestDataSeeder.ADMIN_EMAIL, KalimaTestDataSeeder.PLAIN_PASSWORD);
        UUID lawyerId = createExtraStaff("Attorney Candidate One", DomainEnums.SystemRole.LAWYER);

        mockMvc.perform(post(BASE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("{\"personId\": \"" + lawyerId + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.person.id").value(lawyerId.toString()))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.isDefault").value(false));
    }

    @Test
    void create_nonStaffPerson_rejected() throws Exception {
        String token = obtainToken(KalimaTestDataSeeder.ADMIN_EMAIL, KalimaTestDataSeeder.PLAIN_PASSWORD);
        UUID clientId = requirePersonId(KalimaTestDataSeeder.CLIENT_EMAIL);

        mockMvc.perform(post(BASE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("{\"personId\": \"" + clientId + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_duplicatePerson_rejected() throws Exception {
        String token = obtainToken(KalimaTestDataSeeder.ADMIN_EMAIL, KalimaTestDataSeeder.PLAIN_PASSWORD);
        UUID lawyerId = createExtraStaff("Attorney Candidate Duplicate", DomainEnums.SystemRole.LAWYER);

        mockMvc.perform(post(BASE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("{\"personId\": \"" + lawyerId + "\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(post(BASE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("{\"personId\": \"" + lawyerId + "\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void settingDefault_clearsPreviousDefault_and_deactivating_clearsDefaultFlag() throws Exception {
        String token = obtainToken(KalimaTestDataSeeder.ADMIN_EMAIL, KalimaTestDataSeeder.PLAIN_PASSWORD);
        UUID firstId = createExtraStaff("Attorney Default First", DomainEnums.SystemRole.SENIOR_LAWYER);
        UUID secondId = createExtraStaff("Attorney Default Second", DomainEnums.SystemRole.SENIOR_LAWYER);

        String firstAttorneyId = createAttorney(token, firstId, true);
        String secondAttorneyId = createAttorney(token, secondId, false);

        mockMvc.perform(patch(BASE + "/" + secondAttorneyId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("{\"isDefault\": true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isDefault").value(true));

        mockMvc.perform(patch(BASE + "/" + firstAttorneyId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isDefault").value(false));

        // Deactivating the current default clears its default flag.
        mockMvc.perform(patch(BASE + "/" + secondAttorneyId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("{\"active\": false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false))
                .andExpect(jsonPath("$.isDefault").value(false));
    }

    @Test
    void list_activeOnly_excludesInactive() throws Exception {
        String token = obtainToken(KalimaTestDataSeeder.ADMIN_EMAIL, KalimaTestDataSeeder.PLAIN_PASSWORD);
        UUID activeId = createExtraStaff("Attorney Active Listing", DomainEnums.SystemRole.LAWYER);
        UUID inactiveId = createExtraStaff("Attorney Inactive Listing", DomainEnums.SystemRole.LAWYER);

        createAttorney(token, activeId, false);
        String inactiveAttorneyId = createAttorney(token, inactiveId, false);
        mockMvc.perform(patch(BASE + "/" + inactiveAttorneyId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("{\"active\": false}"))
                .andExpect(status().isOk());

        MvcResult activeOnly = mockMvc.perform(get(BASE)
                        .param("activeOnly", "true")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        String activeOnlyJson = activeOnly.getResponse().getContentAsString();
        org.assertj.core.api.Assertions.assertThat(activeOnlyJson).contains(activeId.toString());
        org.assertj.core.api.Assertions.assertThat(activeOnlyJson).doesNotContain(inactiveId.toString());
    }

    @Test
    void matchByName_findsExactAndPartialMatches() throws Exception {
        String token = obtainToken(KalimaTestDataSeeder.ADMIN_EMAIL, KalimaTestDataSeeder.PLAIN_PASSWORD);
        UUID attorneyPersonId = createExtraStaff("Majid Nasser Sultan Al-Badr", DomainEnums.SystemRole.ADMIN);
        createAttorney(token, attorneyPersonId, false);

        mockMvc.perform(get(BASE + "/match")
                        .param("name", "Majid Nasser Sultan Al-Badr")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matched").value(true))
                .andExpect(jsonPath("$.matchType").value("EXACT"))
                .andExpect(jsonPath("$.person.id").value(attorneyPersonId.toString()));

        mockMvc.perform(get(BASE + "/match")
                        .param("name", "السيد/ Majid Nasser Sultan Al-Badr")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matched").value(true))
                .andExpect(jsonPath("$.matchType").value("EXACT"));

        mockMvc.perform(get(BASE + "/match")
                        .param("name", "Someone Entirely Unrelated")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matched").value(false))
                .andExpect(jsonPath("$.matchType").value("NONE"));
    }

    @Test
    void writeEndpoints_forbidden_forNonAdminRole() throws Exception {
        String lawyerToken = obtainToken(KalimaTestDataSeeder.LAWYER_EMAIL, KalimaTestDataSeeder.PLAIN_PASSWORD);
        UUID candidateId = createExtraStaff("Attorney Forbidden Candidate", DomainEnums.SystemRole.LAWYER);

        mockMvc.perform(post(BASE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + lawyerToken)
                        .content("{\"personId\": \"" + candidateId + "\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void delete_removesAttorney() throws Exception {
        String token = obtainToken(KalimaTestDataSeeder.ADMIN_EMAIL, KalimaTestDataSeeder.PLAIN_PASSWORD);
        UUID candidateId = createExtraStaff("Attorney To Delete", DomainEnums.SystemRole.LAWYER);
        String attorneyId = createAttorney(token, candidateId, false);

        mockMvc.perform(delete(BASE + "/" + attorneyId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(patch(BASE + "/" + attorneyId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("{\"active\": false}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void delete_defaultAttorney_leavesNoDefaultBehind() throws Exception {
        String token = obtainToken(KalimaTestDataSeeder.ADMIN_EMAIL, KalimaTestDataSeeder.PLAIN_PASSWORD);
        UUID candidateId = createExtraStaff("Attorney Default To Delete", DomainEnums.SystemRole.LAWYER);
        String attorneyId = createAttorney(token, candidateId, true);

        mockMvc.perform(delete(BASE + "/" + attorneyId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        // No leftover default row, and creating a fresh default afterwards must
        // still succeed (proves the partial unique index has no dangling row).
        UUID replacementId = createExtraStaff("Attorney Default Replacement", DomainEnums.SystemRole.LAWYER);
        mockMvc.perform(post(BASE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("{\"personId\": \"" + replacementId + "\", \"isDefault\": true}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.isDefault").value(true));
    }

    @Test
    void create_withIsDefaultTrue_whileAnotherDefaultExists_clearsPreviousDefault() throws Exception {
        String token = obtainToken(KalimaTestDataSeeder.ADMIN_EMAIL, KalimaTestDataSeeder.PLAIN_PASSWORD);
        UUID firstId = createExtraStaff("Attorney Create-Time Default First", DomainEnums.SystemRole.LAWYER);
        UUID secondId = createExtraStaff("Attorney Create-Time Default Second", DomainEnums.SystemRole.LAWYER);

        String firstAttorneyId = createAttorney(token, firstId, true);

        mockMvc.perform(post(BASE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("{\"personId\": \"" + secondId + "\", \"isDefault\": true}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.isDefault").value(true));

        MvcResult listRes = mockMvc.perform(get(BASE)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        com.fasterxml.jackson.databind.JsonNode list =
                objectMapper.readTree(listRes.getResponse().getContentAsString());
        boolean firstStillDefault = false;
        for (com.fasterxml.jackson.databind.JsonNode node : list) {
            if (node.get("id").asText().equals(firstAttorneyId)) {
                firstStillDefault = node.get("isDefault").asBoolean();
            }
        }
        org.assertj.core.api.Assertions.assertThat(firstStillDefault).isFalse();
    }

    @Test
    void deletingOfficeAttorney_doesNotAffectExistingDelegationAgent() throws Exception {
        String token = obtainToken(KalimaTestDataSeeder.ADMIN_EMAIL, KalimaTestDataSeeder.PLAIN_PASSWORD);
        UUID attorneyPersonId = createExtraStaff("Attorney Linked To Delegation", DomainEnums.SystemRole.LAWYER);
        String attorneyId = createAttorney(token, attorneyPersonId, false);
        UUID clientId = requirePersonId(KalimaTestDataSeeder.CLIENT_EMAIL);

        String createBody = """
                {
                  "clientId": "%s",
                  "agentId": "%s",
                  "delegationType": "GENERAL",
                  "issuingCountry": "QA",
                  "issuedAt": "%s"
                }
                """.formatted(clientId, attorneyPersonId, LocalDate.now());
        MvcResult delegationRes = mockMvc.perform(post("/api/delegations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(createBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.agent.id").value(attorneyPersonId.toString()))
                .andReturn();
        String delegationId = objectMapper.readTree(delegationRes.getResponse().getContentAsString())
                .get("id").asText();

        // Removing the office-attorney "candidate list" entry must not touch the
        // delegation, which references the Person directly (not the settings row).
        mockMvc.perform(delete(BASE + "/" + attorneyId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/delegations/" + delegationId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.agent.id").value(attorneyPersonId.toString()));
    }

    @Test
    void matchByName_partialMatch() throws Exception {
        String token = obtainToken(KalimaTestDataSeeder.ADMIN_EMAIL, KalimaTestDataSeeder.PLAIN_PASSWORD);
        UUID attorneyPersonId = createExtraStaff("ماجد ناصر سلطان البدر", DomainEnums.SystemRole.ADMIN);
        createAttorney(token, attorneyPersonId, false);

        // Partial: only part of the full name extracted by OCR.
        mockMvc.perform(get(BASE + "/match")
                        .param("name", "ماجد ناصر")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matched").value(true))
                .andExpect(jsonPath("$.matchType").value("PARTIAL"))
                .andExpect(jsonPath("$.person.id").value(attorneyPersonId.toString()));
    }

    @Test
    void matchByName_arabicDiacriticsAndAlefVariants_stillMatchExactly() throws Exception {
        String token = obtainToken(KalimaTestDataSeeder.ADMIN_EMAIL, KalimaTestDataSeeder.PLAIN_PASSWORD);
        // Stored with a hamza-Alef ("أ"); OCR/typed input commonly uses the plain
        // Alef ("ا") and/or adds diacritics — both must still resolve to EXACT.
        UUID attorneyPersonId = createExtraStaff("أحمد راشد المهندي", DomainEnums.SystemRole.ADMIN);
        createAttorney(token, attorneyPersonId, false);

        mockMvc.perform(get(BASE + "/match")
                        .param("name", "احمد راشد المهندي")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matched").value(true))
                .andExpect(jsonPath("$.matchType").value("EXACT"))
                .andExpect(jsonPath("$.person.id").value(attorneyPersonId.toString()));

        // "ماجِد" with a single kasra (U+0650) diacritic — must strip cleanly to "ماجد".
        UUID secondAttorneyPersonId = createExtraStaff("ماجد سالم", DomainEnums.SystemRole.ADMIN);
        createAttorney(token, secondAttorneyPersonId, false);
        mockMvc.perform(get(BASE + "/match")
                        .param("name", "ما\u0650جد سالم")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matched").value(true))
                .andExpect(jsonPath("$.matchType").value("EXACT"))
                .andExpect(jsonPath("$.person.id").value(secondAttorneyPersonId.toString()));
    }

    @Test
    void authorizationMatrix_secretary_and_accountant() throws Exception {
        UUID secretaryPersonId = createExtraStaff("Attorney Auth Secretary", DomainEnums.SystemRole.SECRETARY);
        UUID accountantPersonId = createExtraStaff("Attorney Auth Accountant", DomainEnums.SystemRole.ACCOUNTANT);
        String secretaryToken = loginExtraStaff(secretaryPersonId);
        String accountantToken = loginExtraStaff(accountantPersonId);

        UUID candidateId = createExtraStaff("Attorney Auth Candidate", DomainEnums.SystemRole.LAWYER);

        // SECRETARY can read delegations, but Settings mutation is ADMIN/SENIOR_LAWYER only.
        mockMvc.perform(post(BASE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + secretaryToken)
                        .content("{\"personId\": \"" + candidateId + "\"}"))
                .andExpect(status().isForbidden());

        // ACCOUNTANT can see the management list...
        mockMvc.perform(get(BASE)
                        .header("Authorization", "Bearer " + accountantToken))
                .andExpect(status().isOk());

        // ...but cannot use the delegation-creation helpers (mirrors that ACCOUNTANT
        // cannot create delegations either).
        mockMvc.perform(get(BASE + "/auto")
                        .header("Authorization", "Bearer " + accountantToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(BASE + "/match")
                        .param("name", "anything")
                        .header("Authorization", "Bearer " + accountantToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void concurrentDefaultSwitch_resultsInExactlyOneDefault_noUnhandledError() throws Exception {
        String token = obtainToken(KalimaTestDataSeeder.ADMIN_EMAIL, KalimaTestDataSeeder.PLAIN_PASSWORD);
        UUID firstId = createExtraStaff("Attorney Concurrent First", DomainEnums.SystemRole.LAWYER);
        UUID secondId = createExtraStaff("Attorney Concurrent Second", DomainEnums.SystemRole.LAWYER);
        String firstAttorneyId = createAttorney(token, firstId, false);
        String secondAttorneyId = createAttorney(token, secondId, false);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Callable<Integer> setFirstDefault = () -> mockMvc.perform(patch(BASE + "/" + firstAttorneyId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("Authorization", "Bearer " + token)
                            .content("{\"isDefault\": true}"))
                    .andReturn().getResponse().getStatus();
            Callable<Integer> setSecondDefault = () -> mockMvc.perform(patch(BASE + "/" + secondAttorneyId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("Authorization", "Bearer " + token)
                            .content("{\"isDefault\": true}"))
                    .andReturn().getResponse().getStatus();

            List<Future<Integer>> results = pool.invokeAll(List.of(setFirstDefault, setSecondDefault));
            for (Future<Integer> result : results) {
                int status = result.get(10, TimeUnit.SECONDS);
                // Either the write succeeds, or it loses the race and surfaces as a
                // conflict — never an unhandled 500.
                org.assertj.core.api.Assertions.assertThat(status).isIn(200, 409);
            }
        } finally {
            pool.shutdown();
        }

        MvcResult listRes = mockMvc.perform(get(BASE)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        com.fasterxml.jackson.databind.JsonNode list =
                objectMapper.readTree(listRes.getResponse().getContentAsString());
        long defaultCount = 0;
        for (com.fasterxml.jackson.databind.JsonNode node : list) {
            if (node.get("isDefault").asBoolean()) {
                defaultCount++;
            }
        }
        org.assertj.core.api.Assertions.assertThat(defaultCount).isEqualTo(1);
    }

    private String createAttorney(String token, UUID personId, boolean isDefault) throws Exception {
        MvcResult res = mockMvc.perform(post(BASE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("{\"personId\": \"" + personId + "\", \"isDefault\": " + isDefault + "}"))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString()).get("id").asText();
    }

    private UUID createExtraStaff(String fullName, DomainEnums.SystemRole role) {
        TenantContext.set(Company.DEFAULT_ID);
        try {
            Person p = new Person();
            p.setCompany(companyRepository.getReferenceById(Company.DEFAULT_ID));
            p.setPersonType(DomainEnums.PersonType.INDIVIDUAL);
            p.setSystemRole(role);
            p.setFullName(fullName);
            p.setEmail(fullName.toLowerCase(Locale.ROOT).replace(" ", "-").replace("/", "-") + "@kalima.test");
            if (role != DomainEnums.SystemRole.CLIENT) {
                p.setPasswordHash(passwordEncoder.encode(EXTRA_STAFF_PASSWORD));
            }
            p.setActive(true);
            return personRepository.save(p).getId();
        } finally {
            TenantContext.clear();
        }
    }

    private String loginExtraStaff(UUID personId) throws Exception {
        String email = personRepository.findById(personId)
                .orElseThrow(() -> new IllegalStateException("Missing created staff: " + personId))
                .getEmail();
        return obtainToken(email, EXTRA_STAFF_PASSWORD);
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
