package com.example.test.controller;

import com.example.test.TestAccounts;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Base for end-to-end HTTP tests: real security filter chain, real JWTs, real database. */
@SpringBootTest
@AutoConfigureMockMvc
abstract class ApiTestSupport {

    /** Bootstrap admin from application.properties defaults. */
    static final String ADMIN_EMAIL = "admin@wallet.local";
    static final String ADMIN_PASSWORD = "Admin@12345";
    static final String PASSWORD = "Passw0rd!";
    static final String PIN = "2580";

    @Autowired
    MockMvc mockMvc;
    @Autowired
    ObjectMapper objectMapper;

    /** A registered and logged-in customer. */
    record Customer(String email, String accountNumber, String phoneAccountNumber, String token) {
    }

    Customer newCustomer(String firstName, String lastName) throws Exception {
        String email = firstName.toLowerCase() + "-" + UUID.randomUUID() + "@example.com";
        String body = """
                {"firstName":"%s","lastName":"%s","email":"%s","phoneNumber":"%s","password":"%s","pin":"%s"}
                """.formatted(firstName, lastName, email, TestAccounts.randomPhone(), PASSWORD, PIN);
        JsonNode account = json(mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
        return new Customer(email, account.get("accountNumber").asText(),
                account.get("phoneAccountNumber").asText(), login(email, PASSWORD));
    }

    String login(String email, String password) throws Exception {
        String body = "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
        JsonNode token = json(mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        return token.get("accessToken").asText();
    }

    String adminToken() throws Exception {
        return login(ADMIN_EMAIL, ADMIN_PASSWORD);
    }

    void fund(String accountNumber, String amount) throws Exception {
        mockMvc.perform(withToken(post("/api/v1/admin/accounts/{n}/fund", accountNumber), adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": " + amount + "}"))
                .andExpect(status().isCreated());
    }

    String transfer(Customer from, String toAccount, String amount) throws Exception {
        return json(mockMvc.perform(withToken(post("/api/v1/transfers"), from.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transferJson(toAccount, amount)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("reference").asText();
    }

    static MockHttpServletRequestBuilder withToken(MockHttpServletRequestBuilder request, String token) {
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

    static String transferJson(String toAccount, String amount) {
        return transferJson(toAccount, amount, PIN);
    }

    static String transferJson(String toAccount, String amount, String pin) {
        return "{\"toAccount\":\"" + toAccount + "\",\"amount\":" + amount + ",\"pin\":\"" + pin + "\"}";
    }

    JsonNode json(String content) throws Exception {
        return objectMapper.readTree(content);
    }
}
