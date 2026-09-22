package uz.payflow.api;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** The whole stack over HTTP: security filter, validation, controllers, services, Flyway schema. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PaymentFlowIntegrationTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper json;

    @Test
    void registerDepositTransferAndSeeItFromBothSides() throws Exception {
        String alice = register("Alice Tester");
        String bob = register("Bob Tester");
        JsonNode aliceAccount = firstAccount(alice);
        JsonNode bobAccount = firstAccount(bob);
        long aliceAccountId = aliceAccount.get("id").asLong();
        String bobNumber = bobAccount.get("number").asText();

        mvc.perform(authed(post("/api/accounts/" + aliceAccountId + "/deposits"), alice)
                        .content("{\"amount\": 1000.00, \"description\": \"Зарплата\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.direction").value("IN"));

        mvc.perform(authed(get("/api/accounts/lookup").param("number", bobNumber), alice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ownerName").value("Bob T."));

        String transfer = "{\"fromAccountId\": %d, \"toAccountNumber\": \"%s\", \"amount\": 250.00}"
                .formatted(aliceAccountId, bobNumber);
        mvc.perform(authed(post("/api/transfers"), alice).header("Idempotency-Key", "retry-key-001").content(transfer))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.direction").value("OUT"));

        // The client timed out and sends the very same request again: no second charge.
        mvc.perform(authed(post("/api/transfers"), alice).header("Idempotency-Key", "retry-key-001").content(transfer))
                .andExpect(status().isOk())
                .andExpect(header().string("Idempotent-Replayed", "true"));

        mvc.perform(authed(get("/api/accounts/" + aliceAccountId), alice))
                .andExpect(jsonPath("$.balance").value(750.00));

        mvc.perform(authed(get("/api/operations"), bob))
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].direction").value("IN"))
                .andExpect(jsonPath("$.items[0].counterpartyName").value("Alice T."));

        mvc.perform(authed(get("/api/operations").param("type", "TRANSFER"), alice))
                .andExpect(jsonPath("$.totalItems").value(1));

        mvc.perform(authed(get("/api/stats/monthly").param("months", "3"), alice))
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[2].income").value(1000.00))
                .andExpect(jsonPath("$[2].expense").value(250.00));
    }

    @Test
    void refusesTransferWithoutEnoughMoney() throws Exception {
        String alice = register("Alice Poor");
        String bob = register("Bob Poor");
        long aliceAccountId = firstAccount(alice).get("id").asLong();
        String bobNumber = firstAccount(bob).get("number").asText();

        mvc.perform(authed(post("/api/transfers"), alice)
                        .content("{\"fromAccountId\": %d, \"toAccountNumber\": \"%s\", \"amount\": 1.00}"
                                .formatted(aliceAccountId, bobNumber)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_FUNDS"));
    }

    @Test
    void someoneElsesAccountLooksLikeAMissingOne() throws Exception {
        String alice = register("Alice Curious");
        String bob = register("Bob Private");
        long bobAccountId = firstAccount(bob).get("id").asLong();

        mvc.perform(authed(get("/api/accounts/" + bobAccountId), alice))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ACCOUNT_NOT_FOUND"));
    }

    @Test
    void rejectsRequestsWithoutAToken() throws Exception {
        mvc.perform(get("/api/accounts")).andExpect(status().isUnauthorized());
    }

    @Test
    void reportsEveryInvalidField() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"not-an-email\", \"password\": \"short\", \"fullName\": \"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors.email").exists())
                .andExpect(jsonPath("$.errors.password").exists())
                .andExpect(jsonPath("$.errors.fullName").exists());
    }

    @Test
    void validatesQueryParametersToo() throws Exception {
        String alice = register("Alice Typo");
        mvc.perform(authed(get("/api/accounts/lookup").param("number", "123"), alice))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void emailCanBeRegisteredOnlyOnceAndWrongPasswordIsRefused() throws Exception {
        String email = UUID.randomUUID() + "@test.uz";
        String body = "{\"email\": \"%s\", \"password\": \"password123\", \"fullName\": \"Dup User\"}".formatted(email);
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(body.replace(email, email.toUpperCase())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_TAKEN"));
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"%s\", \"password\": \"wrong-password\"}".formatted(email)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("BAD_CREDENTIALS"));
    }

    private String register(String name) throws Exception {
        String body = "{\"email\": \"%s@test.uz\", \"password\": \"password123\", \"fullName\": \"%s\"}"
                .formatted(UUID.randomUUID(), name);
        String response = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(response).get("accessToken").asText();
    }

    private JsonNode firstAccount(String token) throws Exception {
        String response = mvc.perform(authed(get("/api/accounts"), token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(response).get(0);
    }

    private static MockHttpServletRequestBuilder authed(MockHttpServletRequestBuilder request, String token) {
        return request.header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON);
    }
}
