package com.kalima.backend.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kalima.backend.domain.repository.PersonRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Mandates (إنابة) module — core CRUD, workflow, payments and permissions.
 */
@DisplayName("Mandates module — core workflow")
class MandateIntegrationTest extends AbstractPostgresSpringBootTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private PersonRepository personRepository;

    private String adminToken;

    @Test
    void create_copiesCaseDetails_and_generatesMandateNumber() throws Exception {
        String token = adminToken();
        UUID clientId = requirePersonId(KalimaTestDataSeeder.CLIENT_EMAIL);
        UUID originalLawyerId = requirePersonId(KalimaTestDataSeeder.ADMIN_EMAIL);
        UUID substitutedLawyerId = requirePersonId(KalimaTestDataSeeder.LAWYER_EMAIL);
        String caseId = createCase(token, clientId, originalLawyerId);

        String body = mandateBody(caseId, clientId, originalLawyerId, substitutedLawyerId);

        mockMvc.perform(post("/api/mandates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.mandateNumber", notNullValue()))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.priority").value("HIGH"))
                .andExpect(jsonPath("$.caseNumber").value("MANDATE-IT-101"))
                .andExpect(jsonPath("$.caseTitle").value("Mandate IT case"))
                .andExpect(jsonPath("$.courtName").value("محكمة الاستئناف"))
                .andExpect(jsonPath("$.substitutedLawyer.id").value(substitutedLawyerId.toString()))
                .andExpect(jsonPath("$.originalLawyer.id").value(originalLawyerId.toString()));
    }

    @Test
    void changeStatus_toConfirmed_emitsTimelineEvent() throws Exception {
        String token = adminToken();
        UUID clientId = requirePersonId(KalimaTestDataSeeder.CLIENT_EMAIL);
        UUID originalLawyerId = requirePersonId(KalimaTestDataSeeder.ADMIN_EMAIL);
        UUID substitutedLawyerId = requirePersonId(KalimaTestDataSeeder.LAWYER_EMAIL);
        String caseId = createCase(token, clientId, originalLawyerId);
        String id = createMandate(token, caseId, clientId, originalLawyerId, substitutedLawyerId);

        mockMvc.perform(patch("/api/mandates/" + id + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("{\"status\":\"CONFIRMED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        mockMvc.perform(get("/api/mandates/" + id + "/timeline")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.type=='CREATED')]").exists())
                .andExpect(jsonPath("$[?(@.type=='CONFIRMED')]").exists());
    }

    @Test
    void cancel_requiresReason_and_recordsCancelled() throws Exception {
        String token = adminToken();
        UUID clientId = requirePersonId(KalimaTestDataSeeder.CLIENT_EMAIL);
        UUID originalLawyerId = requirePersonId(KalimaTestDataSeeder.ADMIN_EMAIL);
        UUID substitutedLawyerId = requirePersonId(KalimaTestDataSeeder.LAWYER_EMAIL);
        String caseId = createCase(token, clientId, originalLawyerId);
        String id = createMandate(token, caseId, clientId, originalLawyerId, substitutedLawyerId);

        mockMvc.perform(patch("/api/mandates/" + id + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("{\"status\":\"CANCELLED\"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(patch("/api/mandates/" + id + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("{\"status\":\"CANCELLED\",\"cancelledReason\":\"انسحاب المحامي\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.cancelledReason").value("انسحاب المحامي"))
                .andExpect(jsonPath("$.closedAt", notNullValue()));
    }

    @Test
    void recordPayment_marksPaid_whenFullFeesCovered() throws Exception {
        String token = adminToken();
        UUID clientId = requirePersonId(KalimaTestDataSeeder.CLIENT_EMAIL);
        UUID originalLawyerId = requirePersonId(KalimaTestDataSeeder.ADMIN_EMAIL);
        UUID substitutedLawyerId = requirePersonId(KalimaTestDataSeeder.LAWYER_EMAIL);
        String caseId = createCase(token, clientId, originalLawyerId);
        String body = mandateBody(caseId, clientId, originalLawyerId, substitutedLawyerId)
                .replace("\"fees\": 5000.00", "\"fees\": 5000.00, \"amountPaid\": 2000.00");
        MvcResult created = mockMvc.perform(post("/api/mandates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.paymentStatus").value("PARTIALLY_PAID"))
                .andReturn();
        String id = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(post("/api/mandates/" + id + "/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("""
                                {"amount": 3000.00, "paymentDate": "%s", "method": "TRANSFER"}
                                """.formatted(LocalDate.now())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.amount").value(3000.0));

        mockMvc.perform(get("/api/mandates/" + id)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentStatus").value("PAID"))
                .andExpect(jsonPath("$.remaining").value(0.0));

        mockMvc.perform(get("/api/mandates/" + id + "/payments")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void recordSessionResult_thenComplete_closesMandate() throws Exception {
        String token = adminToken();
        UUID clientId = requirePersonId(KalimaTestDataSeeder.CLIENT_EMAIL);
        UUID originalLawyerId = requirePersonId(KalimaTestDataSeeder.ADMIN_EMAIL);
        UUID substitutedLawyerId = requirePersonId(KalimaTestDataSeeder.LAWYER_EMAIL);
        String caseId = createCase(token, clientId, originalLawyerId);
        String id = createMandate(token, caseId, clientId, originalLawyerId, substitutedLawyerId);

        mockMvc.perform(patch("/api/mandates/" + id + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("{\"status\":\"COMPLETED\"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/mandates/" + id + "/session-result")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("""
                                {"didAttend": true, "sessionResult": "حكم لصالح الموكل", "courtDecision": "رفض الدعوى"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionResult").value("حكم لصالح الموكل"))
                .andExpect(jsonPath("$.didAttend").value(true));

        mockMvc.perform(patch("/api/mandates/" + id + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("{\"status\":\"COMPLETED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.closedAt", notNullValue()));
    }

    @Test
    void create_rejects_identicalOriginalAndSubstitutedLawyer() throws Exception {
        String token = adminToken();
        UUID clientId = requirePersonId(KalimaTestDataSeeder.CLIENT_EMAIL);
        UUID lawyerId = requirePersonId(KalimaTestDataSeeder.LAWYER_EMAIL);
        String caseId = createCase(token, clientId, lawyerId);

        mockMvc.perform(post("/api/mandates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(mandateBody(caseId, clientId, lawyerId, lawyerId)))
                .andExpect(status().isBadRequest());
    }

    private String createMandate(String token, String caseId, UUID clientId,
                                 UUID originalLawyerId, UUID substitutedLawyerId) throws Exception {
        MvcResult r = mockMvc.perform(post("/api/mandates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(mandateBody(caseId, clientId, originalLawyerId, substitutedLawyerId)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(r.getResponse().getContentAsString()).get("id").asText();
    }

    private String createCase(String token, UUID clientId, UUID lawyerId) throws Exception {
        String caseBody = """
                {
                  "caseNumber": "MANDATE-IT-101",
                  "title": "Mandate IT case",
                  "clientId": "%s",
                  "assignedLawyerId": "%s"
                }
                """.formatted(clientId, lawyerId);
        MvcResult caseRes = mockMvc.perform(post("/api/cases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(caseBody))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(caseRes.getResponse().getContentAsString()).get("id").asText();
    }

    private String mandateBody(String caseId, UUID clientId, UUID originalLawyerId,
                               UUID substitutedLawyerId) {
        return """
                {
                  "caseId": "%s",
                  "clientId": "%s",
                  "originalLawyerId": "%s",
                  "substitutedLawyerId": "%s",
                  "priority": "HIGH",
                  "sessionDate": "%s",
                  "courtName": "محكمة الاستئناف",
                  "courtCircuit": "الدائرة المدنية",
                  "courtroom": "3",
                  "reason": "مرافعة في جلسة",
                  "notes": "ملاحظات الإنابة",
                  "fees": 5000.00,
                  "currency": "SAR"
                }
                """.formatted(caseId, clientId, originalLawyerId, substitutedLawyerId,
                LocalDate.now().plusDays(2) + "T10:00:00");
    }

    private String adminToken() throws Exception {
        if (adminToken == null) {
            adminToken = obtainToken(KalimaTestDataSeeder.ADMIN_EMAIL);
        }
        return adminToken;
    }

    private UUID requirePersonId(String email) {
        return personRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("Missing seeded person: " + email))
                .getId();
    }

    private String obtainToken(String email) throws Exception {
        String r = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email
                                + "\",\"password\":\"" + KalimaTestDataSeeder.PLAIN_PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(r).get("accessToken").asText();
    }
}
