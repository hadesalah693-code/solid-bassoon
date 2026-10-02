package com.kalima.backend.integration;

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
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Labor complaints module")
class LaborComplaintIntegrationTest extends AbstractPostgresSpringBootTest {

    @Autowired
    private org.springframework.test.web.servlet.MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private PersonRepository personRepository;

    @Test
    void create_list_get_save_and_referral() throws Exception {
        String token = obtainToken(KalimaTestDataSeeder.ADMIN_EMAIL, KalimaTestDataSeeder.PLAIN_PASSWORD);
        UUID clientId = requirePersonId(KalimaTestDataSeeder.CLIENT_EMAIL);
        UUID lawyerId = requirePersonId(KalimaTestDataSeeder.LAWYER_EMAIL);

        String createBody = """
                {
                  "registrationDate": "%s",
                  "receiptDate": "%s",
                  "subject": "مستحقات مالية وإنهاء تعسفي",
                  "summary": "شكوى عمالية بخصوص مستحقات نهاية الخدمة",
                  "legalRequest": "إلزام صاحب العمل بسداد المستحقات",
                  "clientId": "%s",
                  "assignedLawyerId": "%s",
                  "opponents": [{"name": "شركة المقاولات", "capacity": "DEFENDANT"}]
                }
                """.formatted(LocalDate.now(), LocalDate.now(), clientId, lawyerId);

        MvcResult createRes = mockMvc.perform(post("/api/labor-complaints")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(createBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.complaintNumber").value(startsWith("LW-")))
                .andExpect(jsonPath("$.clients.length()").value(1))
                .andExpect(jsonPath("$.opponents.length()").value(1))
                .andReturn();

        String id = objectMapper.readTree(createRes.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(get("/api/labor-complaints")
                        .header("Authorization", "Bearer " + token)
                        .param("search", "مستحقات"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(greaterThanOrEqualTo(1)));

        mockMvc.perform(get("/api/labor-complaints/" + id)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subject").value("مستحقات مالية وإنهاء تعسفي"));

        mockMvc.perform(patch("/api/labor-complaints/" + id + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("""
                                {"status":"SAVED","savedReason":"تم فض النزاع في إدارة العمل"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SAVED"))
                .andExpect(jsonPath("$.savedReason").value("تم فض النزاع في إدارة العمل"));

        mockMvc.perform(put("/api/labor-complaints/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("""
                                {"internalReference":"INT-2026-001"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.internalReference").value("INT-2026-001"));
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
