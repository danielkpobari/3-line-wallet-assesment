package com.example.test.controller;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Authentication, authorisation and data-ownership rules. */
class SecurityIntegrationTest extends ApiTestSupport {

    @Test
    void protectedEndpointsRequireAToken() throws Exception {
        mockMvc.perform(get("/api/v1/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer"))
                .andExpect(header().exists("X-Request-Id"))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        mockMvc.perform(post("/api/v1/transfers").contentType(MediaType.APPLICATION_JSON)
                        .content(transferJson("1234567890", "1.00")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tamperedOrGarbageTokensAreRejected() throws Exception {
        Customer customer = newCustomer("Eve", "Mallory");
        String tampered = customer.token().substring(0, customer.token().length() - 4) + "abcd";

        mockMvc.perform(withToken(get("/api/v1/me"), tampered))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        mockMvc.perform(withToken(get("/api/v1/me"), "not-a-jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unknownEmailIsToldToRegisterAndWrongPasswordIsInvalidCredentials() throws Exception {
        Customer customer = newCustomer("Sade", "Ali");
        String wrongPassword = "{\"email\":\"" + customer.email() + "\",\"password\":\"Wrong1234\"}";
        String unknownEmail = "{\"email\":\"nobody-" + java.util.UUID.randomUUID()
                + "@example.com\",\"password\":\"Wrong1234\"}";

        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(unknownEmail))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("EMAIL_NOT_REGISTERED"))
                .andExpect(jsonPath("$.message").value("No account is registered with this email. Please register first."));
        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(wrongPassword))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.message").value("Incorrect password"));
    }

    @Test
    void customersCannotUseAdminEndpoints() throws Exception {
        Customer customer = newCustomer("Joy", "Uche");

        mockMvc.perform(withToken(get("/api/v1/admin/transactions"), customer.token()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mockMvc.perform(withToken(post("/api/v1/admin/accounts/{n}/fund", customer.accountNumber()), customer.token())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":1000000}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void customersCannotSeeOtherCustomersTransactions() throws Exception {
        Customer sender = newCustomer("Ifeanyi", "Nwosu");
        Customer receiver = newCustomer("Amaka", "Nwosu");
        Customer outsider = newCustomer("Segun", "Bello");
        fund(sender.accountNumber(), "20.00");
        String reference = transfer(sender, receiver.accountNumber(), "5.00");

        // Responds 404, not 403, so references belonging to others cannot be confirmed to exist.
        mockMvc.perform(withToken(get("/api/v1/transactions/{ref}", reference), outsider.token()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TRANSACTION_NOT_FOUND"));

        mockMvc.perform(withToken(get("/api/v1/admin/transactions/{ref}", reference), adminToken()))
                .andExpect(status().isOk());
    }

    @Test
    void transferAlwaysDebitsTheCallerEvenIfABodyNamesAnotherSource() throws Exception {
        Customer victim = newCustomer("Rich", "Person");
        Customer attacker = newCustomer("Sly", "Fox");
        fund(victim.accountNumber(), "500.00");

        // An extra "fromAccount" field is ignored: the source is resolved from the token.
        mockMvc.perform(withToken(post("/api/v1/transfers"), attacker.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fromAccount\":\"" + victim.accountNumber() + "\",\"toAccount\":\""
                                + attacker.accountNumber() + "\",\"amount\":100.00,\"pin\":\"" + PIN + "\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("SAME_ACCOUNT_TRANSFER"));

        mockMvc.perform(withToken(get("/api/v1/me/balance"), victim.token()))
                .andExpect(jsonPath("$.balance").value(500.00));
    }

    @Test
    void publicEndpointsDoNotNeedAToken() throws Exception {
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mockMvc.perform(get("/api-docs")).andExpect(status().isOk());
    }
}
