package com.kalima.backend.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import static com.kalima.backend.integration.MessagingSecurityTestSupport.tenant1AdminToken;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Actuator endpoints — production-safe exposure")
class ActuatorIntegrationTest extends AbstractPostgresSpringBootTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("/actuator/health is public")
    void health_isPublic() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("/actuator/info requires authentication")
    void info_requiresAuth() throws Exception {
        mockMvc.perform(get("/actuator/info"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("/actuator/metrics requires authentication")
    void metrics_requiresAuth() throws Exception {
        mockMvc.perform(get("/actuator/metrics"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("/actuator/metrics is available when authenticated")
    void metrics_whenAuthenticated_returnsOk() throws Exception {
        String token = tenant1AdminToken(mockMvc, objectMapper);
        mockMvc.perform(get("/actuator/metrics")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Sensitive actuator endpoint /actuator/env stays disabled")
    void env_staysDisabled() throws Exception {
        String token = tenant1AdminToken(mockMvc, objectMapper);
        mockMvc.perform(get("/actuator/env")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }
}
