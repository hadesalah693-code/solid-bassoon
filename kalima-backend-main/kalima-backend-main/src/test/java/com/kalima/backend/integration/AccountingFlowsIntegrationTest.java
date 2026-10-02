package com.kalima.backend.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kalima.backend.domain.repository.PersonRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.util.UUID;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AccountingFlowsIntegrationTest extends AbstractPostgresSpringBootTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private PersonRepository personRepository;

    @Test
    void bankTransfer_postsBalancedDebitToDestinationAndCreditToSource() throws Exception {
        String token = token();
        String source = ensureBankAccount(token, "1100", "Integration cash");
        String destination = ensureBankAccount(token, "1110", "Integration bank");
        String date = LocalDate.now().toString();

        MvcResult transfer = mockMvc.perform(post("/api/accounting/bank-accounts/transfers")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sourceBankAccountId":"%s","destinationBankAccountId":"%s",
                                 "amount":125.00,"transferDate":"%s","reference":"IT-TRANSFER"}
                                """.formatted(source, destination, date)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.journalEntryNumber").isNotEmpty())
                .andReturn();
        String journalId = objectMapper.readTree(transfer.getResponse().getContentAsString())
                .get("journalEntryId").asText();

        mockMvc.perform(get("/api/accounting/journal/" + journalId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("BANK_TRANSFER"))
                .andExpect(jsonPath("$.totalDebit").value(125.0))
                .andExpect(jsonPath("$.totalCredit").value(125.0));
        mockMvc.perform(get("/api/accounting/reports/overview")
                        .param("from", date).param("to", date)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trialBalanced").value(true))
                .andExpect(jsonPath("$.balanced").value(true));
    }

    @Test
    void invoiceReceiptAndRetainerAllocation_keepArAndRevenueBalanced() throws Exception {
        String token = token();
        UUID clientId = personRepository.findByEmail(KalimaTestDataSeeder.CLIENT_EMAIL).orElseThrow().getId();
        String date = LocalDate.now().toString();
        String invoiceId = createIssuedInvoice(token, clientId, "1000.00");
        String bankAccountId = ensureBankAccount(token, "1110", "Integration bank");

        MvcResult receiptResult = mockMvc.perform(post("/api/accounting/receipts")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"receiptDate":"%s","bankAccountId":"%s","amount":1000.00,
                                 "clientId":"%s","invoiceId":"%s","currency":"SAR","paymentMethod":"BANK_TRANSFER"}
                                """.formatted(date, bankAccountId, clientId, invoiceId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.invoiceId").value(invoiceId))
                .andReturn();
        String receiptJournalId = objectMapper.readTree(receiptResult.getResponse().getContentAsString())
                .get("journalEntryId").asText();

        mockMvc.perform(get("/api/invoices/" + invoiceId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amountPaid").value(1000.0))
                .andExpect(jsonPath("$.status").value("PAID"));
        mockMvc.perform(get("/api/accounting/journal/" + receiptJournalId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalDebit").value(1000.0))
                .andExpect(jsonPath("$.totalCredit").value(1000.0))
                .andExpect(jsonPath("$.lines[*].accountCode").value(hasItem("1200")));

        String retainer = mockMvc.perform(post("/api/accounting/retainers")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"clientId":"%s","amount":500.00,"currency":"SAR","receivedDate":"%s"}
                                """.formatted(clientId, date)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.remainingAmount").value(500.0))
                .andReturn().getResponse().getContentAsString();
        String retainerId = objectMapper.readTree(retainer).get("id").asText();
        String invoiceForRetainer = createIssuedInvoice(token, clientId, "500.00");

        MvcResult application = mockMvc.perform(post("/api/accounting/retainers/" + retainerId + "/apply")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"retainerId":"%s","appliedAmount":500.00,"appliedDate":"%s",
                                 "targetType":"INVOICE","targetId":"%s"}
                                """.formatted(retainerId, date, invoiceForRetainer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.targetReference").isNotEmpty())
                .andReturn();
        String applicationJournalId = objectMapper.readTree(application.getResponse().getContentAsString())
                .get("journalEntryId").asText();

        mockMvc.perform(get("/api/accounting/journal/" + applicationJournalId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalDebit").value(500.0))
                .andExpect(jsonPath("$.totalCredit").value(500.0))
                .andExpect(jsonPath("$.lines[*].accountCode").value(hasItem("2300")))
                .andExpect(jsonPath("$.lines[*].accountCode").value(hasItem("1200")));
        mockMvc.perform(get("/api/invoices/" + invoiceForRetainer)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amountPaid").value(500.0))
                .andExpect(jsonPath("$.status").value("PAID"));
        mockMvc.perform(get("/api/accounting/reports/overview")
                        .param("from", date).param("to", date)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trialBalanced").value(true))
                .andExpect(jsonPath("$.balanced").value(true));
    }

    private String createIssuedInvoice(String token, UUID clientId, String subtotal) throws Exception {
        String date = LocalDate.now().toString();
        MvcResult created = mockMvc.perform(post("/api/invoices")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"clientId":"%s","issueDate":"%s","dueDate":"%s","subtotal":%s,"taxRate":0}
                                """.formatted(clientId, date, date, subtotal)))
                .andExpect(status().isCreated())
                .andReturn();
        String id = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText();
        mockMvc.perform(patch("/api/invoices/" + id + "/status")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ISSUED\"}"))
                .andExpect(status().isOk());
        return id;
    }

    private String ensureBankAccount(String token, String code, String name) throws Exception {
        mockMvc.perform(post("/api/accounting/accounts/seed")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        JsonNode existing = objectMapper.readTree(mockMvc.perform(get("/api/accounting/bank-accounts")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        for (JsonNode bank : existing) {
            if (code.equals(bank.get("accountCode").asText())) return bank.get("id").asText();
        }
        JsonNode accounts = objectMapper.readTree(mockMvc.perform(get("/api/accounting/accounts")
                        .param("type", "ASSET")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        String accountId = null;
        for (JsonNode account : accounts) {
            if (code.equals(account.get("code").asText())) accountId = account.get("id").asText();
        }
        if (accountId == null) throw new AssertionError("Missing seeded asset account " + code);
        MvcResult bank = mockMvc.perform(post("/api/accounting/bank-accounts")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nameAr":"%s","accountId":"%s","currency":"SAR"}
                                """.formatted(name, accountId)))
                .andExpect(status().isCreated()).andReturn();
        return objectMapper.readTree(bank.getResponse().getContentAsString()).get("id").asText();
    }

    private String token() throws Exception {
        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(KalimaTestDataSeeder.ADMIN_EMAIL, KalimaTestDataSeeder.PLAIN_PASSWORD)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("accessToken").asText();
    }
}