package it.russello.contocorrente.controller;

import it.russello.contocorrente.exception.ApiExceptionHandler;
import it.russello.contocorrente.service.AccountService;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AccountController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(ApiExceptionHandler.class)
public class ControllerValidationTest {
    @Autowired
    MockMvc mockMvc;
    @MockitoBean
    AccountService accountService;

    private static String quote(String value) {
        if (value == null) {
            return "null";
        }
        return "\"" + value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n") + "\"";
    }


    static Stream<InvalidRequest> invalidRequests() {
        List<InvalidRequest> cases = new ArrayList<>();
        // Numero conto.
        for (String number : new String[]{null, "", "   ", "a".repeat(31)}) {
            for (String method : List.of("POST", "PUT")) {
                cases.add(new InvalidRequest(
                        method,
                        method.equals("POST") ? "/api/account" : "/api/account/1",
                        "{\"accountNumber\":" + quote(number) + "}",
                        "INVALID_ACCOUNT_DATA"));
            }
        }

        for (long personId : new long[]{0, -1}) {
            cases.add(new InvalidRequest("POST", "/api/account",
                    """
                            {"personId":%d,"accountNumber":"ACC-001"}
                            """.formatted(personId),
                    "INVALID_ACCOUNT_DATA"));
        }

        // Proprietà JSON sconosciute.
        for (String url : List.of(
                "/api/account")) {

            cases.add(new InvalidRequest(
                    "POST", url, "{\"unexpected\":true}", "INVALID_REQUEST"));
        }

        return cases.stream();
    }

    @ParameterizedTest
    @MethodSource("invalidRequests")
    void invalidDataDoesNotReachService(InvalidRequest test) throws Exception {
        var request = test.method().equals("PUT")
                ? put(test.url())
                : post(test.url());

        mockMvc.perform(request
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(test.json()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(test.code()))
                .andExpect(jsonPath("$.message").isNotEmpty());

        verifyNoInteractions(accountService);
    }


    record InvalidRequest(String method, String url, String json, String code) {
    }
}

