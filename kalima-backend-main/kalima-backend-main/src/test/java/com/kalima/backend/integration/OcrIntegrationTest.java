package com.kalima.backend.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("OCR module API")
class OcrIntegrationTest extends AbstractPostgresSpringBootTest {

    @Autowired
    private org.springframework.test.web.servlet.MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void upload_list_get_update_download_delete_roundtrip() throws Exception {
        String token = obtainToken(KalimaTestDataSeeder.ADMIN_EMAIL, KalimaTestDataSeeder.PLAIN_PASSWORD);

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "scan.png",
                "image/png",
                new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A});

        MvcResult uploadRes = mockMvc.perform(multipart("/api/ocr/upload")
                        .file(file)
                        .param("title", "مستند مسح")
                        .param("documentType", "SCAN")
                        .contentType(MediaType.MULTIPART_FORM_DATA)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("مستند مسح"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.originalFilename").value("scan.png"))
                .andReturn();

        String recordId = objectMapper.readTree(uploadRes.getResponse().getContentAsString())
                .get("id")
                .asText();

        mockMvc.perform(get("/api/ocr")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(recordId));

        mockMvc.perform(get("/api/ocr/" + recordId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documentType").value("SCAN"));

        mockMvc.perform(get("/api/ocr/stats")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalDocuments").isNumber());

        mockMvc.perform(put("/api/ocr/" + recordId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("""
                                {"title":"مستند محدث","editedText":"نص تجريبي"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("مستند محدث"))
                .andExpect(jsonPath("$.editedText").value("نص تجريبي"));

        mockMvc.perform(get("/api/ocr/" + recordId + "/download")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(r -> assertThat(r.getResponse().getContentAsByteArray().length).isPositive());

        mockMvc.perform(get("/api/ocr/" + recordId + "/text/download")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(r -> assertThat(new String(r.getResponse().getContentAsByteArray()))
                        .contains("نص تجريبي"));

        mockMvc.perform(delete("/api/ocr/" + recordId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/ocr/" + recordId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void client_cannot_access_ocr() throws Exception {
        String token = obtainToken(KalimaTestDataSeeder.CLIENT_EMAIL, KalimaTestDataSeeder.PLAIN_PASSWORD);

        mockMvc.perform(get("/api/ocr")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejects_unsupported_file_type() throws Exception {
        String token = obtainToken(KalimaTestDataSeeder.ADMIN_EMAIL, KalimaTestDataSeeder.PLAIN_PASSWORD);
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "notes.txt",
                "text/plain",
                "hello".getBytes());

        mockMvc.perform(multipart("/api/ocr/upload")
                        .file(file)
                        .contentType(MediaType.MULTIPART_FORM_DATA)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("Unsupported file type. Allowed: JPG, JPEG, PNG, PDF.")));
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
