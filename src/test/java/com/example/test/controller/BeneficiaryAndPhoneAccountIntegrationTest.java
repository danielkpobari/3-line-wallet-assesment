package com.example.test.controller;

import com.example.test.TestAccounts;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Receiving via the phone account number, and transferring to saved beneficiaries. */
class BeneficiaryAndPhoneAccountIntegrationTest extends ApiTestSupport {

    // ------------------------------------------------------------------ phone account number

    @Test
    void registrationNormalisesThePhoneAndDerivesThePhoneAccountNumber() throws Exception {
        String local = TestAccounts.randomPhone();
        String national = local.substring(1);
        String email = "phone-" + UUID.randomUUID() + "@example.com";

        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                        {"firstName":"Emeka","lastName":"Obi","email":"%s","phoneNumber":"%s","password":"%s","pin":"%s"}
                        """.formatted(email, local, PASSWORD, PIN)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.phoneNumber").value("+234" + national))
                .andExpect(jsonPath("$.phoneAccountNumber").value(national));

        // The same number in international format is a duplicate.
        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                        {"firstName":"Other","lastName":"Person","email":"%s","phoneNumber":"+234%s","password":"%s","pin":"%s"}
                        """.formatted("other-" + UUID.randomUUID() + "@example.com", national, PASSWORD, PIN)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_PHONE"));
    }

    @Test
    void moneySentToThePhoneAccountNumberLandsInTheSameWallet() throws Exception {
        Customer sender = newCustomer("Ada", "Sender");
        Customer receiver = newCustomer("Bayo", "Receiver");
        fund(sender.accountNumber(), "100.00");

        mockMvc.perform(withToken(get("/api/v1/accounts/{n}/name-enquiry", receiver.phoneAccountNumber()),
                        sender.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountNumber").value(receiver.accountNumber()))
                .andExpect(jsonPath("$.accountName").value("Bayo Receiver"));

        mockMvc.perform(withToken(post("/api/v1/transfers"), sender.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transferJson(receiver.phoneAccountNumber(), "40.00")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.toAccount").value(receiver.accountNumber()));

        mockMvc.perform(withToken(get("/api/v1/me/balance"), receiver.token()))
                .andExpect(jsonPath("$.balance").value(40.00));
    }

    @Test
    void cannotSendToYourOwnPhoneAccountNumber() throws Exception {
        Customer customer = newCustomer("Self", "Payer");
        fund(customer.accountNumber(), "50.00");

        mockMvc.perform(withToken(post("/api/v1/transfers"), customer.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transferJson(customer.phoneAccountNumber(), "10.00")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("SAME_ACCOUNT_TRANSFER"));
    }

    // ------------------------------------------------------------------ beneficiaries

    @Test
    void addListAndTransferToABeneficiary() throws Exception {
        Customer me = newCustomer("Kunle", "Owner");
        Customer mum = newCustomer("Funke", "Owner");
        fund(me.accountNumber(), "500.00");

        long id = json(mockMvc.perform(withToken(post("/api/v1/me/beneficiaries"), me.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accountNumber\":\"" + mum.phoneAccountNumber() + "\",\"nickname\":\"Mum\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accountNumber").value(mum.accountNumber()))
                .andExpect(jsonPath("$.phoneAccountNumber").value(mum.phoneAccountNumber()))
                .andExpect(jsonPath("$.accountName").value("Funke Owner"))
                .andExpect(jsonPath("$.nickname").value("Mum"))
                .andReturn().getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(withToken(get("/api/v1/me/beneficiaries"), me.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].id").value(id));
        mockMvc.perform(withToken(get("/api/v1/me/beneficiaries").param("q", "mum"), me.token()))
                .andExpect(jsonPath("$.totalElements").value(1));
        mockMvc.perform(withToken(get("/api/v1/me/beneficiaries").param("q", "funke"), me.token()))
                .andExpect(jsonPath("$.totalElements").value(1));
        mockMvc.perform(withToken(get("/api/v1/me/beneficiaries").param("q", "nobody"), me.token()))
                .andExpect(jsonPath("$.totalElements").value(0));

        mockMvc.perform(withToken(post("/api/v1/transfers/beneficiaries/{id}", id), me.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":120.00,\"narration\":\"For Mum\",\"pin\":\"" + PIN + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.toAccount").value(mum.accountNumber()));

        mockMvc.perform(withToken(get("/api/v1/me/balance"), mum.token()))
                .andExpect(jsonPath("$.balance").value(120.00));
    }

    @Test
    void beneficiaryRulesAreEnforced() throws Exception {
        Customer me = newCustomer("Rule", "Keeper");
        Customer friend = newCustomer("Good", "Friend");
        String add = "{\"accountNumber\":\"" + friend.accountNumber() + "\"}";

        mockMvc.perform(withToken(post("/api/v1/me/beneficiaries"), me.token())
                        .contentType(MediaType.APPLICATION_JSON).content(add))
                .andExpect(status().isCreated());
        // Same account again, even by its other number, is a duplicate.
        mockMvc.perform(withToken(post("/api/v1/me/beneficiaries"), me.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accountNumber\":\"" + friend.phoneAccountNumber() + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BENEFICIARY_EXISTS"));
        mockMvc.perform(withToken(post("/api/v1/me/beneficiaries"), me.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accountNumber\":\"" + me.accountNumber() + "\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INVALID_BENEFICIARY"));
        mockMvc.perform(withToken(post("/api/v1/me/beneficiaries"), me.token())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"accountNumber\":\"1000000000\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ACCOUNT_NOT_FOUND"));
    }

    @Test
    void customersCannotSeeUseOrDeleteOtherCustomersBeneficiaries() throws Exception {
        Customer owner = newCustomer("Bene", "Owner");
        Customer target = newCustomer("Bene", "Target");
        Customer intruder = newCustomer("Bene", "Intruder");
        fund(intruder.accountNumber(), "100.00");
        long id = json(mockMvc.perform(withToken(post("/api/v1/me/beneficiaries"), owner.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accountNumber\":\"" + target.accountNumber() + "\"}"))
                .andReturn().getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(withToken(get("/api/v1/me/beneficiaries"), intruder.token()))
                .andExpect(jsonPath("$.totalElements").value(0));
        mockMvc.perform(withToken(post("/api/v1/transfers/beneficiaries/{id}", id), intruder.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":10.00,\"pin\":\"" + PIN + "\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("BENEFICIARY_NOT_FOUND"));
        mockMvc.perform(withToken(delete("/api/v1/me/beneficiaries/{id}", id), intruder.token()))
                .andExpect(status().isNotFound());

        mockMvc.perform(withToken(delete("/api/v1/me/beneficiaries/{id}", id), owner.token()))
                .andExpect(status().isNoContent());
        mockMvc.perform(withToken(get("/api/v1/me/beneficiaries"), owner.token()))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void transferCanSaveTheRecipientAsABeneficiary() throws Exception {
        Customer sender = newCustomer("Save", "Sender");
        Customer receiver = newCustomer("Save", "Receiver");
        fund(sender.accountNumber(), "100.00");

        mockMvc.perform(withToken(post("/api/v1/transfers"), sender.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"toAccount\":\"" + receiver.phoneAccountNumber() + "\",\"amount\":5.00,\"pin\":\""
                                + PIN + "\",\"saveAsBeneficiary\":true}"))
                .andExpect(status().isCreated());

        mockMvc.perform(withToken(get("/api/v1/me/beneficiaries"), sender.token()))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].accountNumber").value(receiver.accountNumber()));
    }

    @Test
    void plainTransferNeedsAnAccountNumberAndBeneficiaryIsOptional() throws Exception {
        Customer sender = newCustomer("Plain", "Sender");
        Customer receiver = newCustomer("Plain", "Receiver");
        fund(sender.accountNumber(), "50.00");

        mockMvc.perform(withToken(post("/api/v1/transfers"), sender.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":5.00,\"pin\":\"" + PIN + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors.toAccount").exists());

        // No beneficiary involved at all: only toAccount, amount and pin.
        mockMvc.perform(withToken(post("/api/v1/transfers"), sender.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transferJson(receiver.accountNumber(), "5.00")))
                .andExpect(status().isCreated());
    }

    @Test
    void beneficiaryTransferValidatesThePathAndBody() throws Exception {
        Customer sender = newCustomer("Path", "Checker");

        mockMvc.perform(withToken(post("/api/v1/transfers/beneficiaries/{id}", 0), sender.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":5.00,\"pin\":\"" + PIN + "\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(withToken(post("/api/v1/transfers/beneficiaries/{id}", 999_999), sender.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":5.00}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.pin").exists());
    }
}
