package com.example.test.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The audit trail records who did what, including failures whose business change was rolled back. */
class AuditTrailIntegrationTest extends ApiTestSupport {

    @Test
    void successfulTransferIsAuditedWithTheRequestIdFromTheResponseHeader() throws Exception {
        Customer sender = newCustomer("Audit", "Sender");
        Customer receiver = newCustomer("Audit", "Receiver");
        fund(sender.accountNumber(), "100.00");
        String requestId = "audit-" + UUID.randomUUID();

        String reference = json(mockMvc.perform(withToken(post("/api/v1/transfers"), sender.token())
                        .header("X-Request-Id", requestId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transferJson(receiver.phoneAccountNumber(), "25.00")))
                .andExpect(status().isCreated())
                .andExpect(header().string("X-Request-Id", requestId))
                .andReturn().getResponse().getContentAsString()).get("reference").asText();

        JsonNode event = singleEvent(get("/api/v1/admin/audit-events").param("requestId", requestId));
        assertThat(event.get("action").asText()).isEqualTo("TRANSFER_COMPLETED");
        assertThat(event.get("outcome").asText()).isEqualTo("SUCCESS");
        assertThat(event.get("resourceType").asText()).isEqualTo("TRANSACTION");
        assertThat(event.get("resourceId").asText()).isEqualTo(reference);
        assertThat(event.get("actorRole").asText()).isEqualTo("USER");
        assertThat(event.get("details").asText())
                .contains(receiver.accountNumber())
                .contains("25.00");
    }

    @Test
    void failedTransferIsAuditedEvenThoughItRolledBack() throws Exception {
        Customer sender = newCustomer("Broke", "Sender");
        Customer receiver = newCustomer("Broke", "Receiver");
        String requestId = "audit-" + UUID.randomUUID();

        mockMvc.perform(withToken(post("/api/v1/transfers"), sender.token())
                        .header("X-Request-Id", requestId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transferJson(receiver.accountNumber(), "500.00")))
                .andExpect(status().isUnprocessableEntity());

        JsonNode event = singleEvent(get("/api/v1/admin/audit-events").param("requestId", requestId));
        assertThat(event.get("action").asText()).isEqualTo("TRANSFER_FAILED");
        assertThat(event.get("outcome").asText()).isEqualTo("FAILURE");
        assertThat(event.get("errorCode").asText()).isEqualTo("INSUFFICIENT_FUNDS");
    }

    @Test
    void wrongPinIsAuditedAsBothAPinFailureAndAFailedTransfer() throws Exception {
        Customer sender = newCustomer("Pin", "Auditee");
        Customer receiver = newCustomer("Pin", "Target");
        fund(sender.accountNumber(), "50.00");
        String requestId = "audit-" + UUID.randomUUID();

        mockMvc.perform(withToken(post("/api/v1/transfers"), sender.token())
                        .header("X-Request-Id", requestId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transferJson(receiver.accountNumber(), "5.00", "9999")))
                .andExpect(status().isForbidden());

        JsonNode events = searchEvents(get("/api/v1/admin/audit-events").param("requestId", requestId));
        assertThat(events.get("totalElements").asInt()).isEqualTo(2);
        assertThat(events.get("content").findValuesAsText("action"))
                .containsExactlyInAnyOrder("PIN_VERIFICATION_FAILED", "TRANSFER_FAILED");
        assertThat(events.get("content").findValuesAsText("errorCode")).containsOnly("INVALID_PIN");
    }

    @Test
    void loginSuccessAndFailureAreAudited() throws Exception {
        Customer customer = newCustomer("Login", "Auditee");
        String failedRequestId = "audit-" + UUID.randomUUID();

        mockMvc.perform(post("/api/v1/auth/login")
                        .header("X-Request-Id", failedRequestId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + customer.email() + "\",\"password\":\"Wrong1234\"}"))
                .andExpect(status().isUnauthorized());

        JsonNode failed = singleEvent(get("/api/v1/admin/audit-events").param("requestId", failedRequestId));
        assertThat(failed.get("action").asText()).isEqualTo("LOGIN_FAILED");
        assertThat(failed.get("errorCode").asText()).isEqualTo("INVALID_CREDENTIALS");
        assertThat(failed.get("details").asText()).doesNotContain("Wrong1234");

        long userId = failed.get("actorUserId").asLong();
        JsonNode successes = searchEvents(get("/api/v1/admin/audit-events")
                .param("actorUserId", String.valueOf(userId)).param("action", "LOGIN_SUCCEEDED"));
        assertThat(successes.get("totalElements").asInt()).isGreaterThanOrEqualTo(1);

        JsonNode registered = searchEvents(get("/api/v1/admin/audit-events")
                .param("actorUserId", String.valueOf(userId)).param("action", "USER_REGISTERED"));
        assertThat(registered.get("content").get(0).get("resourceId").asText()).isEqualTo(customer.accountNumber());
    }

    @Test
    void adminActionsAreAuditedWithTheAdminAsActor() throws Exception {
        Customer customer = newCustomer("Frozen", "Customer");

        mockMvc.perform(withToken(patch("/api/v1/admin/accounts/{n}/status", customer.accountNumber()), adminToken())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"FROZEN\"}"))
                .andExpect(status().isOk());

        JsonNode event = singleEvent(get("/api/v1/admin/audit-events")
                .param("action", "ACCOUNT_STATUS_CHANGED").param("resourceId", customer.accountNumber()));
        assertThat(event.get("actorRole").asText()).isEqualTo("ADMIN");
        assertThat(event.get("details").asText()).contains("ACTIVE").contains("FROZEN");
    }

    @Test
    void customersCannotReadTheAuditTrail() throws Exception {
        Customer customer = newCustomer("Nosy", "Customer");

        mockMvc.perform(withToken(get("/api/v1/admin/audit-events"), customer.token()))
                .andExpect(status().isForbidden());
    }

    @Test
    void auditSearchRejectsInvalidFilters() throws Exception {
        mockMvc.perform(withToken(get("/api/v1/admin/audit-events").param("action", "NOT_AN_ACTION"), adminToken()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    private JsonNode singleEvent(MockHttpServletRequestBuilder search) throws Exception {
        JsonNode page = searchEvents(search);
        assertThat(page.get("totalElements").asInt()).isEqualTo(1);
        return page.get("content").get(0);
    }

    private JsonNode searchEvents(MockHttpServletRequestBuilder search) throws Exception {
        return json(mockMvc.perform(withToken(search, adminToken()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }
}
