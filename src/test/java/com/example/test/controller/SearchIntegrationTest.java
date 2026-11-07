package com.example.test.controller;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneId;

import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Specification-based search filters on the customer statement and the admin endpoints. */
class SearchIntegrationTest extends ApiTestSupport {

    @Test
    void customerCanFilterOwnStatement() throws Exception {
        Customer me = newCustomer("Kola", "Ade");
        Customer other = newCustomer("Tola", "Ade");
        fund(me.accountNumber(), "1000.00");
        transfer(me, other.accountNumber(), "100.00");
        transfer(me, other.accountNumber(), "300.00");
        transfer(other, me.accountNumber(), "50.00");

        mockMvc.perform(withToken(get("/api/v1/me/transactions").param("entryType", "DEBIT"), me.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[*].entryType", everyItem(is("DEBIT"))));

        mockMvc.perform(withToken(get("/api/v1/me/transactions")
                        .param("entryType", "CREDIT").param("transactionType", "TRANSFER"), me.token()))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].amount").value(50.00));

        mockMvc.perform(withToken(get("/api/v1/me/transactions")
                        .param("minAmount", "100").param("maxAmount", "300"), me.token()))
                .andExpect(jsonPath("$.totalElements").value(2));

        String today = LocalDate.now(ZoneId.of("Africa/Lagos")).toString();
        mockMvc.perform(withToken(get("/api/v1/me/transactions")
                        .param("fromDate", today).param("toDate", today).param("size", "2"), me.token()))
                .andExpect(jsonPath("$.totalElements").value(4))
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.totalPages").value(2));
    }

    @Test
    void adminCanSearchTransactionsWithCombinedFilters() throws Exception {
        Customer a = newCustomer("Musa", "Danjuma");
        Customer b = newCustomer("Hauwa", "Danjuma");
        fund(a.accountNumber(), "10000.00");
        transfer(a, b.accountNumber(), "500.00");
        transfer(a, b.accountNumber(), "7500.00");
        String admin = adminToken();

        mockMvc.perform(withToken(get("/api/v1/admin/transactions")
                        .param("accountNumber", a.accountNumber()), admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3));

        mockMvc.perform(withToken(get("/api/v1/admin/transactions")
                        .param("accountNumber", a.accountNumber())
                        .param("type", "TRANSFER")
                        .param("minAmount", "1000"), admin))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].amount").value(7500.00));
    }

    @Test
    void adminCanSearchAccountsByNameOrEmail() throws Exception {
        Customer customer = newCustomer("Zainab", "Uniquename");
        String admin = adminToken();

        mockMvc.perform(withToken(get("/api/v1/admin/accounts").param("q", "UNIQUENAME"), admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.accountNumber == '" + customer.accountNumber() + "')]").exists());

        mockMvc.perform(withToken(get("/api/v1/admin/accounts")
                        .param("q", customer.email()).param("status", "ACTIVE"), admin))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].email").value(customer.email()));
    }

    @Test
    void invalidSearchFiltersAreRejected() throws Exception {
        Customer customer = newCustomer("Bad", "Filter");

        mockMvc.perform(withToken(get("/api/v1/me/transactions").param("entryType", "SIDEWAYS"), customer.token()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        mockMvc.perform(withToken(get("/api/v1/me/transactions")
                        .param("fromDate", "2026-11-10").param("toDate", "2026-11-01"), customer.token()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        mockMvc.perform(withToken(get("/api/v1/me/transactions").param("size", "500"), customer.token()))
                .andExpect(status().isBadRequest());
    }
}
