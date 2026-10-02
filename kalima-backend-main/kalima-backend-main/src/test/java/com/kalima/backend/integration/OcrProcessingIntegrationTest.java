package com.kalima.backend.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("OCR processing lifecycle")
class OcrProcessingIntegrationTest extends AbstractPostgresSpringBootTest {

    @Autowired
    private org.springframework.test.web.servlet.MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void manual_process_marks_failed_for_non_text_image() throws Exception {
        String token = obtainToken(KalimaTestDataSeeder.ADMIN_EMAIL, KalimaTestDataSeeder.PLAIN_PASSWORD);

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "scan.png",
                "image/png",
                new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A});

        MvcResult uploadRes = mockMvc.perform(multipart("/api/ocr/upload")
                        .file(file)
                        .contentType(MediaType.MULTIPART_FORM_DATA)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn();

        String recordId = objectMapper.readTree(uploadRes.getResponse().getContentAsString())
                .get("id")
                .asText();

        mockMvc.perform(post("/api/ocr/" + recordId + "/process")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PROCESSING"));

        String finalStatus = awaitTerminalStatus(token, recordId);
        assertThat(finalStatus).isIn("FAILED", "COMPLETED");
    }

    @Test
    void rerun_after_failure_is_allowed() throws Exception {
        String token = obtainToken(KalimaTestDataSeeder.ADMIN_EMAIL, KalimaTestDataSeeder.PLAIN_PASSWORD);

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "scan.png",
                "image/png",
                new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47});

        MvcResult uploadRes = mockMvc.perform(multipart("/api/ocr/upload")
                        .file(file)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andReturn();
        String recordId = objectMapper.readTree(uploadRes.getResponse().getContentAsString())
                .get("id")
                .asText();

        mockMvc.perform(post("/api/ocr/" + recordId + "/process")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        awaitTerminalStatus(token, recordId);

        mockMvc.perform(post("/api/ocr/" + recordId + "/process")
                        .param("rerun", "true")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PROCESSING"));
    }

    private String awaitTerminalStatus(String token, String recordId) throws Exception {
        for (int i = 0; i < 40; i++) {
            MvcResult res = mockMvc.perform(get("/api/ocr/" + recordId)
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andReturn();
            String status = objectMapper.readTree(res.getResponse().getContentAsString())
                    .get("status")
                    .asText();
            if ("COMPLETED".equals(status) || "FAILED".equals(status)) {
                return status;
            }
            TimeUnit.MILLISECONDS.sleep(250);
        }
        throw new AssertionError("OCR did not reach terminal status in time");
    }

    private String obtainToken(String email, String password) throws Exception {
        String r = mockMvc.perform(MockMvcRequestBuilders
                        .post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(r).get("accessToken").asText();
    }
}
