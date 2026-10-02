package com.kalima.backend.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kalima.backend.agent.night.dto.NightAgentReport;
import com.kalima.backend.domain.entity.DomainEnums;
import com.kalima.backend.domain.entity.Person;
import com.kalima.backend.domain.repository.PersonRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("AI Agent layer — voice + night agent")
class AgentLayerIntegrationTest extends AbstractPostgresSpringBootTest {

    @Autowired
    private org.springframework.test.web.servlet.MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private PersonRepository personRepository;

    @Test
    void voiceAgent_capabilitiesReply() throws Exception {
        String token = obtainToken(KalimaTestDataSeeder.ADMIN_EMAIL, KalimaTestDataSeeder.PLAIN_PASSWORD);
        mockMvc.perform(post("/api/agent/voice")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("{\"text\":\"اى اللى بتقدر تعمله\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reply").value(org.hamcrest.Matchers.containsString("اعرض القضايا")))
                .andExpect(jsonPath("$.toolsUsed").isEmpty());
    }

    @Test
    void voiceAgent_listCasesEgyptianArabic() throws Exception {
        String token = obtainToken(KalimaTestDataSeeder.ADMIN_EMAIL, KalimaTestDataSeeder.PLAIN_PASSWORD);
        mockMvc.perform(post("/api/agent/voice")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("{\"text\":\"ورينى كل القضايا\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reply").isNotEmpty())
                .andExpect(jsonPath("$.toolsUsed[0]").value("list_cases"));
    }

    @Test
    void voiceAgent_listCases_fallbackWithoutGemini() throws Exception {
        String token = obtainToken(KalimaTestDataSeeder.ADMIN_EMAIL, KalimaTestDataSeeder.PLAIN_PASSWORD);
        String body = """
                {"text":"اعرض قائمة القضايا"}
                """;

        mockMvc.perform(post("/api/agent/voice")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reply").isNotEmpty())
                .andExpect(jsonPath("$.needsConfirmation").value(false))
                .andExpect(jsonPath("$.toolsUsed").isArray());
    }

    @Test
    void voiceAgent_unauthenticated_returns401() throws Exception {
        mockMvc.perform(post("/api/agent/voice")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"اعرض القضايا\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void speechConfig_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/agent/speech/config"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void voiceAgent_pendingActionWithoutConfirm_returns400() throws Exception {
        String token = obtainToken(KalimaTestDataSeeder.ADMIN_EMAIL, KalimaTestDataSeeder.PLAIN_PASSWORD);
        String body = """
                {"text":"تأكيد","pendingActionId":"%s"}
                """.formatted(UUID.randomUUID());

        mockMvc.perform(post("/api/agent/voice")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void voiceAgent_closeCaseRequiresConfirmationThenExecutes() throws Exception {
        String token = obtainToken(KalimaTestDataSeeder.ADMIN_EMAIL, KalimaTestDataSeeder.PLAIN_PASSWORD);
        UUID clientId = MessagingSecurityTestSupport.requirePersonId(
                personRepository, KalimaTestDataSeeder.CLIENT_EMAIL);
        UUID lawyerId = MessagingSecurityTestSupport.requirePersonId(
                personRepository, KalimaTestDataSeeder.LAWYER_EMAIL);
        String caseId = MessagingSecurityTestSupport.createCase(
                mockMvc, objectMapper, token, clientId, lawyerId, "Agent Close");

        mockMvc.perform(post("/api/agent/voice")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("{\"text\":\"ملخص " + caseId + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activeCaseId").value(caseId));

        MvcResult pending = mockMvc.perform(post("/api/agent/voice")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("{\"text\":\"اغلق القضية\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.needsConfirmation").value(true))
                .andExpect(jsonPath("$.pendingActionId").isNotEmpty())
                .andReturn();

        JsonNode pendingJson = objectMapper.readTree(pending.getResponse().getContentAsString());
        String pendingId = pendingJson.get("pendingActionId").asText();

        mockMvc.perform(post("/api/agent/voice")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("""
                                {"text":"تأكيد","pendingActionId":"%s","confirm":true}
                                """.formatted(pendingId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.needsConfirmation").value(false))
                .andExpect(jsonPath("$.reply").value(containsString("تم إغلاق")));
    }

    @Test
    void voiceAgent_secretaryCannotConfirmCloseCase() throws Exception {
        Person mutable = personRepository.findByEmail(KalimaTestDataSeeder.MUTABLE_LAWYER_EMAIL)
                .orElseThrow();
        try {
            mutable.setSystemRole(DomainEnums.SystemRole.SECRETARY);
            personRepository.saveAndFlush(mutable);

            String adminToken = obtainToken(
                    KalimaTestDataSeeder.ADMIN_EMAIL, KalimaTestDataSeeder.PLAIN_PASSWORD);
            String secretaryToken = obtainToken(
                    KalimaTestDataSeeder.MUTABLE_LAWYER_EMAIL, KalimaTestDataSeeder.PLAIN_PASSWORD);
            UUID clientId = MessagingSecurityTestSupport.requirePersonId(
                    personRepository, KalimaTestDataSeeder.CLIENT_EMAIL);
            UUID lawyerId = MessagingSecurityTestSupport.requirePersonId(
                    personRepository, KalimaTestDataSeeder.LAWYER_EMAIL);
            String caseId = MessagingSecurityTestSupport.createCase(
                    mockMvc, objectMapper, adminToken, clientId, lawyerId, "Secretary RBAC");

            mockMvc.perform(post("/api/agent/voice")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("Authorization", "Bearer " + secretaryToken)
                            .content("{\"text\":\"ملخص " + caseId + "\"}"))
                    .andExpect(status().isOk());

            MvcResult pending = mockMvc.perform(post("/api/agent/voice")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("Authorization", "Bearer " + secretaryToken)
                            .content("{\"text\":\"اغلق القضية\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.needsConfirmation").value(true))
                    .andReturn();

            String pendingId = objectMapper.readTree(pending.getResponse().getContentAsString())
                    .get("pendingActionId").asText();

            mockMvc.perform(post("/api/agent/voice")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("Authorization", "Bearer " + secretaryToken)
                            .content("""
                                    {"text":"تأكيد","pendingActionId":"%s","confirm":true}
                                    """.formatted(pendingId)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.needsConfirmation").value(false))
                    .andExpect(jsonPath("$.reply").value(containsString("لا تملك صلاحية")));
        } finally {
            mutable.setSystemRole(DomainEnums.SystemRole.LAWYER);
            personRepository.saveAndFlush(mutable);
        }
    }

    @Test
    void nightAgent_runAndFetchReport() throws Exception {
        String token = obtainToken(KalimaTestDataSeeder.ADMIN_EMAIL, KalimaTestDataSeeder.PLAIN_PASSWORD);

        MvcResult run = mockMvc.perform(post("/api/agent/night/run")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.highRiskCases").isArray())
                .andExpect(jsonPath("$.warnings").isArray())
                .andExpect(jsonPath("$.suggestions").isArray())
                .andExpect(jsonPath("$.autoActions").isArray())
                .andReturn();

        NightAgentReport report = objectMapper.readValue(
                run.getResponse().getContentAsString(), NightAgentReport.class);
        assertThat(report.highRiskCases()).isNotNull();

        mockMvc.perform(get("/api/agent/night/report")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.warnings").isArray());
    }

    private String obtainToken(String email, String password) throws Exception {
        String r = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode n = objectMapper.readTree(r);
        return n.get("accessToken").asText();
    }
}
