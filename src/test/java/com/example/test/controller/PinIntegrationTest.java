package com.example.test.controller;

import com.example.test.TestAccounts;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Transaction PIN: set at registration, required for transfers, locked after repeated wrong attempts. */
class PinIntegrationTest extends ApiTestSupport {

    @Test
    void transferRequiresAPin() throws Exception {
        Customer sender = newCustomer("Pin", "Missing");
        Customer receiver = newCustomer("Pin", "Receiver");
        fund(sender.accountNumber(), "100.00");

        mockMvc.perform(withToken(post("/api/v1/transfers"), sender.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"toAccount\":\"" + receiver.accountNumber() + "\",\"amount\":10.00}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors.pin").exists());
    }

    @Test
    void wrongPinIsRejectedAndMovesNoMoney() throws Exception {
        Customer sender = newCustomer("Wrong", "Pin");
        Customer receiver = newCustomer("Wrong", "Receiver");
        fund(sender.accountNumber(), "100.00");

        mockMvc.perform(withToken(post("/api/v1/transfers"), sender.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transferJson(receiver.accountNumber(), "10.00", "9999")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("INVALID_PIN"))
                .andExpect(jsonPath("$.message", containsString("2 attempt(s) remaining")));

        assertBalance(sender, 100.00);
        assertBalance(receiver, 0.00);
    }

    @Test
    void pinLocksAfterThreeWrongAttemptsEvenForTheCorrectPin() throws Exception {
        Customer sender = newCustomer("Locked", "Out");
        Customer receiver = newCustomer("Locked", "Receiver");
        fund(sender.accountNumber(), "100.00");

        for (String remaining : new String[]{"2 attempt(s)", "1 attempt(s)"}) {
            mockMvc.perform(withToken(post("/api/v1/transfers"), sender.token())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(transferJson(receiver.accountNumber(), "10.00", "9999")))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.message", containsString(remaining)));
        }
        mockMvc.perform(withToken(post("/api/v1/transfers"), sender.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transferJson(receiver.accountNumber(), "10.00", "9999")))
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.code").value("PIN_LOCKED"));

        // Locked: even the right PIN is refused until the lock expires.
        mockMvc.perform(withToken(post("/api/v1/transfers"), sender.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transferJson(receiver.accountNumber(), "10.00", PIN)))
                .andExpect(status().isLocked());

        assertBalance(sender, 100.00);
    }

    @Test
    void correctPinResetsTheFailedAttemptCounter() throws Exception {
        Customer sender = newCustomer("Reset", "Counter");
        Customer receiver = newCustomer("Reset", "Receiver");
        fund(sender.accountNumber(), "100.00");

        wrongPinTransfer(sender, receiver);
        wrongPinTransfer(sender, receiver);
        transfer(sender, receiver.accountNumber(), "10.00");

        // Back to a full set of attempts.
        mockMvc.perform(withToken(post("/api/v1/transfers"), sender.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transferJson(receiver.accountNumber(), "10.00", "9999")))
                .andExpect(jsonPath("$.message", containsString("2 attempt(s) remaining")));
    }

    @Test
    void customerCanChangePinAndOnlyTheNewPinWorksAfterwards() throws Exception {
        Customer sender = newCustomer("Change", "Pin");
        Customer receiver = newCustomer("Change", "Receiver");
        fund(sender.accountNumber(), "100.00");

        mockMvc.perform(withToken(put("/api/v1/me/pin"), sender.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPin\":\"" + PIN + "\",\"newPin\":\"7391\"}"))
                .andExpect(status().isNoContent());

        mockMvc.perform(withToken(post("/api/v1/transfers"), sender.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transferJson(receiver.accountNumber(), "10.00", PIN)))
                .andExpect(status().isForbidden());
        mockMvc.perform(withToken(post("/api/v1/transfers"), sender.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transferJson(receiver.accountNumber(), "10.00", "7391")))
                .andExpect(status().isCreated());
    }

    @Test
    void changePinRejectsWrongCurrentPinAndWeakNewPin() throws Exception {
        Customer customer = newCustomer("Weak", "Pin");

        mockMvc.perform(withToken(put("/api/v1/me/pin"), customer.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPin\":\"9999\",\"newPin\":\"7391\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("INVALID_PIN"));

        mockMvc.perform(withToken(put("/api/v1/me/pin"), customer.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPin\":\"" + PIN + "\",\"newPin\":\"1234\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void registrationRejectsWeakPins() throws Exception {
        for (String weak : new String[]{"0000", "1234", "9876"}) {
            String body = """
                    {"firstName":"Weak","lastName":"Pin","email":"%s","phoneNumber":"%s","password":"%s","pin":"%s"}
                    """.formatted("weak-" + UUID.randomUUID() + "@example.com", TestAccounts.randomPhone(),
                    PASSWORD, weak);
            mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }
    }

    private void wrongPinTransfer(Customer sender, Customer receiver) throws Exception {
        mockMvc.perform(withToken(post("/api/v1/transfers"), sender.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transferJson(receiver.accountNumber(), "10.00", "9999")))
                .andExpect(status().isForbidden());
    }

    private void assertBalance(Customer customer, double expected) throws Exception {
        mockMvc.perform(withToken(get("/api/v1/me/balance"), customer.token()))
                .andExpect(jsonPath("$.balance").value(expected));
    }
}
