package com.kalima.backend.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kalima.backend.domain.repository.PersonRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.util.UUID;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Criminal reports module")
class CriminalReportIntegrationTest extends AbstractPostgresSpringBootTest {

    @Autowired
    private org.springframework.test.web.servlet.MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private PersonRepository personRepository;

    @Test
    void create_list_get_and_archive() throws Exception {
        String token = obtainToken(KalimaTestDataSeeder.ADMIN_EMAIL, KalimaTestDataSeeder.PLAIN_PASSWORD);
        UUID clientId = requirePersonId(KalimaTestDataSeeder.CLIENT_EMAIL);
        UUID lawyerId = requirePersonId(KalimaTestDataSeeder.LAWYER_EMAIL);

        String createBody = """
                {
                  "registrationDate": "%s",
                  "incidentDate": "%s",
                  "prosecutionOffice": "نيابة غرب العاصمة",
                  "securityCenter": "إدارة أمن العاصمة",
                  "charge": "الاستيلاء على مال الغير",
                  "subject": "بلاغ بشيك بدون رصيد",
                  "clientId": "%s",
                  "assignedLawyerId": "%s",
                  "opponentName": "خصم تجريبي",
                  "opponentCapacity": "ACCUSED"
                }
                """.formatted(LocalDate.now(), LocalDate.now().minusDays(5), clientId, lawyerId);

        MvcResult createRes = mockMvc.perform(post("/api/criminal-reports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(createBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("UNDER_INVESTIGATION"))
                .andExpect(jsonPath("$.reportNumber").isNotEmpty())
                .andExpect(jsonPath("$.client.id").value(clientId.toString()))
                .andExpect(jsonPath("$.clients.length()").value(1))
                .andExpect(jsonPath("$.opponents.length()").value(1))
                .andExpect(jsonPath("$.incidentDate").value(LocalDate.now().minusDays(5).toString()))
                .andReturn();

        String id = objectMapper.readTree(createRes.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(get("/api/criminal-reports")
                        .header("Authorization", "Bearer " + token)
                        .param("search", "بلاغ"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(greaterThanOrEqualTo(1)));

        mockMvc.perform(get("/api/criminal-reports/" + id)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.prosecutionOffice").value("نيابة غرب العاصمة"))
                .andExpect(jsonPath("$.charge").value("الاستيلاء على مال الغير"));

        String updateBody = """
                {
                  "summary": "ملخص البلاغ بعد التحديث"
                }
                """;
        mockMvc.perform(put("/api/criminal-reports/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(updateBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary").value("ملخص البلاغ بعد التحديث"));

        String archiveBody = """
                {
                  "status": "ARCHIVED",
                  "archiveReason": "عدم كفاية الأدلة",
                  "grievancePeriodDays": 30
                }
                """;
        mockMvc.perform(patch("/api/criminal-reports/" + id + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(archiveBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ARCHIVED"))
                .andExpect(jsonPath("$.archiveReason").value("عدم كفاية الأدلة"))
                .andExpect(jsonPath("$.grievancePeriodDays").value(30))
                .andExpect(jsonPath("$.grievanceDeadline").isNotEmpty());
    }

    @Test
    void archive_withoutReason_rejected() throws Exception {
        String token = obtainToken(KalimaTestDataSeeder.ADMIN_EMAIL, KalimaTestDataSeeder.PLAIN_PASSWORD);
        UUID clientId = requirePersonId(KalimaTestDataSeeder.CLIENT_EMAIL);

        MvcResult createRes = mockMvc.perform(post("/api/criminal-reports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("""
                                {
                                  "registrationDate": "%s",
                                  "clientId": "%s"
                                }
                                """.formatted(LocalDate.now(), clientId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.incidentDate").value(LocalDate.now().toString()))
                .andReturn();
        String id = objectMapper.readTree(createRes.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(patch("/api/criminal-reports/" + id + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("""
                                {
                                  "status": "ARCHIVED",
                                  "grievancePeriodDays": 15
                                }
                                """))
                .andExpect(status().isBadRequest());
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

    private UUID requirePersonId(String email) {
        return personRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("Missing seeded person: " + email))
                .getId();
    }
}
