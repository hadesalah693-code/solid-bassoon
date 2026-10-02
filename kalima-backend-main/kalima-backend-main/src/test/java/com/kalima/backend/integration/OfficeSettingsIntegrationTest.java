package com.kalima.backend.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kalima.backend.domain.entity.Company;
import com.kalima.backend.domain.repository.CompanyRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Office settings — tenant profile")
class OfficeSettingsIntegrationTest extends AbstractPostgresSpringBootTest {

    private static final String BASE = "/api/office/settings";

    @Autowired
    private org.springframework.test.web.servlet.MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private CompanyRepository companyRepository;

    @Test
    void get_returnsCurrentTenantProfile() throws Exception {
        String adminToken = obtainToken(KalimaTestDataSeeder.ADMIN_EMAIL, KalimaTestDataSeeder.PLAIN_PASSWORD);

        mockMvc.perform(get(BASE)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(Company.DEFAULT_ID.toString()))
                .andExpect(jsonPath("$.name").isNotEmpty())
                .andExpect(jsonPath("$.countryCode").value("SA"))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void update_adminCanChangeProfile() throws Exception {
        String adminToken = obtainToken(KalimaTestDataSeeder.ADMIN_EMAIL, KalimaTestDataSeeder.PLAIN_PASSWORD);

        String body = """
                {
                  "name": "مكتب كلمة القانوني",
                  "legalName": "Kalima Legal Office LLC",
                  "commercialRegister": "1010123456",
                  "taxNumber": "300123456789003",
                  "phone": "+966501234567",
                  "email": "office@kalima.test",
                  "address": "الرياض — حي العليا",
                  "website": "https://kalima.test",
                  "city": "الرياض",
                  "countryCode": "SA"
                }
                """;

        mockMvc.perform(put(BASE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + adminToken)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("مكتب كلمة القانوني"))
                .andExpect(jsonPath("$.legalName").value("Kalima Legal Office LLC"))
                .andExpect(jsonPath("$.commercialRegister").value("1010123456"))
                .andExpect(jsonPath("$.website").value("https://kalima.test"))
                .andExpect(jsonPath("$.city").value("الرياض"));

        Company saved = companyRepository.findById(Company.DEFAULT_ID).orElseThrow();
        assertThat(saved.getName()).isEqualTo("مكتب كلمة القانوني");
        assertThat(saved.getWebsite()).isEqualTo("https://kalima.test");
    }

    @Test
    void update_lawyerForbidden() throws Exception {
        String lawyerToken = obtainToken(KalimaTestDataSeeder.LAWYER_EMAIL, KalimaTestDataSeeder.PLAIN_PASSWORD);

        mockMvc.perform(put(BASE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + lawyerToken)
                        .content("""
                                {
                                  "name": "محاولة غير مصرح بها",
                                  "countryCode": "SA"
                                }
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void update_blankNameRejected() throws Exception {
        String adminToken = obtainToken(KalimaTestDataSeeder.ADMIN_EMAIL, KalimaTestDataSeeder.PLAIN_PASSWORD);

        mockMvc.perform(put(BASE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + adminToken)
                        .content("""
                                {
                                  "name": "   ",
                                  "countryCode": "SA"
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
}
