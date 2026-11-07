package com.example.test.controller;

import com.example.test.TestAccounts;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Happy paths and business errors through the full HTTP stack, authenticated as real users. */
class WalletApiIntegrationTest extends ApiTestSupport {

    @Test
    void registerLoginFundTransferEndToEnd() throws Exception {
        String email = "alice-" + UUID.randomUUID() + "@example.com";
        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName":"Alice","lastName":"Okafor","email":"%s","phoneNumber":"%s",
                                 "password":"%s","pin":"%s"}
                                """.formatted(email, TestAccounts.randomPhone(), PASSWORD, PIN)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accountNumber", matchesPattern("[1-6]\\d{9}")))
                .andExpect(jsonPath("$.phoneAccountNumber", matchesPattern("[789][01]\\d{8}")))
                .andExpect(jsonPath("$.balance").value(0.00))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.pin").doesNotExist());

        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email.toUpperCase() + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.accessToken").isNotEmpty());

        Customer alice = new Customer(email, null, null, login(email, PASSWORD));
        String aliceAccount = json(mockMvc.perform(withToken(get("/api/v1/me"), alice.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email))
                .andReturn().getResponse().getContentAsString()).get("accountNumber").asText();
        Customer bob = newCustomer("Bob", "Adeyemi");

        fund(aliceAccount, "1000.00");

        mockMvc.perform(withToken(get("/api/v1/accounts/{n}/name-enquiry", bob.accountNumber()), alice.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountName").value("Bob Adeyemi"))
                .andExpect(jsonPath("$.email").doesNotExist())
                .andExpect(jsonPath("$.balance").doesNotExist());

        String reference = json(mockMvc.perform(withToken(post("/api/v1/transfers"), alice.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transferJson(bob.accountNumber(), "250.00")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reference", startsWith("TRF")))
                .andExpect(jsonPath("$.fromAccount").value(aliceAccount))
                .andExpect(jsonPath("$.status").value("SUCCESSFUL"))
                .andReturn().getResponse().getContentAsString()).get("reference").asText();

        mockMvc.perform(withToken(get("/api/v1/me/balance"), alice.token()))
                .andExpect(jsonPath("$.balance").value(750.00));
        mockMvc.perform(withToken(get("/api/v1/me/balance"), bob.token()))
                .andExpect(jsonPath("$.balance").value(250.00));

        mockMvc.perform(withToken(get("/api/v1/me/transactions"), alice.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[0].entryType").value("DEBIT"))
                .andExpect(jsonPath("$.content[0].counterpartyAccount").value(bob.accountNumber()))
                .andExpect(jsonPath("$.content[0].balanceAfter").value(750.00))
                .andExpect(jsonPath("$.totalElements").value(2));

        // Both parties can see the transaction.
        mockMvc.perform(withToken(get("/api/v1/transactions/{ref}", reference), alice.token()))
                .andExpect(status().isOk());
        mockMvc.perform(withToken(get("/api/v1/transactions/{ref}", reference), bob.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.toAccount").value(bob.accountNumber()));
    }

    @Test
    void retriedTransferWithSameIdempotencyKeyIsReplayedNotRepeated() throws Exception {
        Customer sender = newCustomer("Chidi", "Eze");
        Customer receiver = newCustomer("Ngozi", "Eze");
        fund(sender.accountNumber(), "100.00");
        String key = UUID.randomUUID().toString();

        mockMvc.perform(withToken(post("/api/v1/transfers"), sender.token()).header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON).content(transferJson(receiver.accountNumber(), "30.00")))
                .andExpect(status().isCreated());
        mockMvc.perform(withToken(post("/api/v1/transfers"), sender.token()).header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON).content(transferJson(receiver.accountNumber(), "30.00")))
                .andExpect(status().isOk())
                .andExpect(header().string("Idempotent-Replayed", "true"));

        mockMvc.perform(withToken(get("/api/v1/me/balance"), sender.token()))
                .andExpect(jsonPath("$.balance").value(70.00));
    }

    @Test
    void registerReturnsFieldErrorsForInvalidInput() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName":"","lastName":"Obi","email":"not-an-email","phoneNumber":"12345",
                                 "password":"short","pin":"12"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors.firstName").exists())
                .andExpect(jsonPath("$.fieldErrors.email").exists())
                .andExpect(jsonPath("$.fieldErrors.password").exists())
                .andExpect(jsonPath("$.fieldErrors.pin").exists())
                .andExpect(jsonPath("$.fieldErrors.phoneNumber").exists());
    }

    @Test
    void duplicateEmailIsRejectedWithConflict() throws Exception {
        String email = "dup-" + UUID.randomUUID() + "@example.com";
        String body = """
                {"firstName":"Ada","lastName":"Obi","email":"%s","phoneNumber":"%s","password":"%s","pin":"%s"}
                """.formatted(email, TestAccounts.randomPhone(), PASSWORD, PIN);
        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_EMAIL"));
    }

    @Test
    void transferValidationAndBusinessErrorsUseStableCodes() throws Exception {
        Customer sender = newCustomer("Tunde", "Bakare");
        Customer receiver = newCustomer("Kemi", "Bakare");

        mockMvc.perform(withToken(post("/api/v1/transfers"), sender.token()).contentType(MediaType.APPLICATION_JSON)
                        .content(transferJson(receiver.accountNumber(), "0")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors.amount").exists());

        mockMvc.perform(withToken(post("/api/v1/transfers"), sender.token()).contentType(MediaType.APPLICATION_JSON)
                        .content(transferJson(receiver.accountNumber(), "5.00")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_FUNDS"));

        mockMvc.perform(withToken(post("/api/v1/transfers"), sender.token()).contentType(MediaType.APPLICATION_JSON)
                        .content(transferJson(sender.accountNumber(), "5.00")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("SAME_ACCOUNT_TRANSFER"));

        mockMvc.perform(withToken(post("/api/v1/transfers"), sender.token()).contentType(MediaType.APPLICATION_JSON)
                        .content(transferJson("0000000000", "5.00")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ACCOUNT_NOT_FOUND"));

        mockMvc.perform(withToken(post("/api/v1/transfers"), sender.token()).contentType(MediaType.APPLICATION_JSON)
                        .content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    @Test
    void frozenAccountCannotSendOrReceive() throws Exception {
        Customer sender = newCustomer("Femi", "Ola");
        Customer receiver = newCustomer("Bisi", "Ola");
        fund(sender.accountNumber(), "50.00");

        mockMvc.perform(withToken(patch("/api/v1/admin/accounts/{n}/status", receiver.accountNumber()), adminToken())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"FROZEN\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FROZEN"));

        mockMvc.perform(withToken(post("/api/v1/transfers"), sender.token()).contentType(MediaType.APPLICATION_JSON)
                        .content(transferJson(receiver.accountNumber(), "10.00")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("ACCOUNT_NOT_ACTIVE"));
    }
}
