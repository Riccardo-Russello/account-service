package it.russello.contocorrente.controller;

import it.russello.contocorrente.dto.AccountBalanceResponse;
import it.russello.contocorrente.dto.AccountCreateRequest;
import it.russello.contocorrente.dto.AccountResponse;
import it.russello.contocorrente.dto.AccountUpdateRequest;
import it.russello.contocorrente.exception.ApiException;
import it.russello.contocorrente.exception.ApiExceptionHandler;
import it.russello.contocorrente.service.AccountService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AccountController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(ApiExceptionHandler.class)
public class AccountControllerTest {
    private static final String BASE_URL = "/api/account";
    private static final String CREATE_JSON = """
            {
                "personId" : 10,
                "accountNumber" : "ACC-001"
            }
            """;
    private static final String UPDATE_JSON = """
            {
                "accountNumber" : "ACC-002"
            }
            """;
    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private AccountService accountService;

    private static AccountCreateRequest createRequest() {
        return new AccountCreateRequest(10L, "ACC-001");
    }

    private static AccountUpdateRequest updateRequest() {
        return new AccountUpdateRequest("ACC-002");
    }

    private static AccountResponse response(String accountNumber) {
        return new AccountResponse(1L, 10L, accountNumber, LocalDateTime.of(2026, 1, 10, 12, 0), new BigDecimal("125.50"));
    }

    @Test
    void createValidAccount() throws Exception {
        when(accountService.create(createRequest())).thenReturn(response("ACC-001"));
        mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_JSON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.personId").value(10))
                .andExpect(jsonPath("$.accountNumber").value("ACC-001"))
                .andExpect(jsonPath("$.openedAt").value("2026-01-10T12:00:00"))
                .andExpect(jsonPath("$.balance").value(125.50));
        verify(accountService).create(createRequest());
    }

    @Test
    void findAllAccounts() throws Exception {
        Pageable pageable = PageRequest.of(0, 10, Sort.by("id"));
        when(accountService.findAll(pageable)).thenReturn(new PageImpl<>(List.of(response("ACC-001")), pageable, 1));
        mockMvc.perform(get(BASE_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(1))
                .andExpect(jsonPath("$.content[0].personId").value(10))
                .andExpect(jsonPath("$.content[0].accountNumber").value("ACC-001"))
                .andExpect(jsonPath("$.content[0].openedAt").value("2026-01-10T12:00:00"))
                .andExpect(jsonPath("$.content[0].balance").value(125.50))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1));
        verify(accountService).findAll(pageable);
    }

    @Test
    void findAllEmpty() throws Exception {
        Pageable pageable = PageRequest.of(0, 10, Sort.by("id"));
        when(accountService.findAll(pageable)).thenReturn(Page.empty(pageable));
        mockMvc.perform(get(BASE_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.totalPages").value(0));
        verify(accountService).findAll(pageable);
    }

    @Test
    void findAllUsesRequestedPaginationAndSort() throws Exception {
        Pageable pageable = PageRequest.of(1, 1, Sort.by(Sort.Direction.DESC, "balance"));
        when(accountService.findAll(pageable)).thenReturn(new PageImpl<>(List.of(response("ACC-001")), pageable, 3));
        mockMvc.perform(get(BASE_URL)
                        .param("page", "1")
                        .param("size", "1")
                        .param("sort", "balance,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(3));
        verify(accountService).findAll(pageable);
    }

    @Test
    void findByIdAccount() throws Exception {
        when(accountService.findById(1L)).thenReturn(response("ACC-001"));
        mockMvc.perform(get(BASE_URL + "/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.personId").value(10))
                .andExpect(jsonPath("$.accountNumber").value("ACC-001"))
                .andExpect(jsonPath("$.openedAt").value("2026-01-10T12:00:00"))
                .andExpect(jsonPath("$.balance").value(125.50));
        verify(accountService).findById(1L);
    }

    @Test
    void getBalanceAndCurrency() throws Exception {
        when(accountService.getBalance(1L))
                .thenReturn(new AccountBalanceResponse(1L, new BigDecimal("125.50"), "€ (EUR)"));
        mockMvc.perform(get(BASE_URL + "/1/balance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value(1))
                .andExpect(jsonPath("$.balance").value(125.50))
                .andExpect(jsonPath("$.currency").value("€ (EUR)"));
        verify(accountService).getBalance(1L);
    }

    @Test
    void updateAccount() throws Exception {
        when(accountService.update(1L, updateRequest())).thenReturn(response("ACC-002"));
        mockMvc.perform(put(BASE_URL + "/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(UPDATE_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.personId").value(10))
                .andExpect(jsonPath("$.accountNumber").value("ACC-002"))
                .andExpect(jsonPath("$.openedAt").value("2026-01-10T12:00:00"))
                .andExpect(jsonPath("$.balance").value(125.50));
        verify(accountService).update(1L, updateRequest());
    }

    @Test
    void deleteAccount() throws Exception {
        mockMvc.perform(delete(BASE_URL + "/1"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
        verify(accountService).delete(1L);
    }

    @ParameterizedTest
    @CsvSource({"404, PERSON_NOT_FOUND",
            "409, ACCOUNT_NUMBER_ALREADY_EXISTS"})
    void createServiceError(int httpStatus, String code) throws Exception {
        when(accountService.create(createRequest())).thenThrow(new ApiException(HttpStatus.valueOf(httpStatus), code, "Errore creazione"));
        mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_JSON))
                .andExpect(status().is(httpStatus))
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.message").value("Errore creazione"));
        verify(accountService).create(createRequest());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/1", "/1/balance"})
    void readMissingAccount(String path) throws Exception {
        ApiException error = new ApiException(HttpStatus.NOT_FOUND, "ACCOUNT_NOT_FOUND", "Conto non trovato");
        if (path.endsWith("/balance")) {
            when(accountService.getBalance(1L)).thenThrow(error);
        } else {
            when(accountService.findById(1L)).thenThrow(error);
        }
        mockMvc.perform(get(BASE_URL + path))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ACCOUNT_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Conto non trovato"));
        if (path.endsWith("/balance")) {
            verify(accountService).getBalance(1L);
        } else {
            verify(accountService).findById(1L);
        }
    }

    @ParameterizedTest
    @CsvSource({"404, ACCOUNT_NOT_FOUND",
            "409, ACCOUNT_NUMBER_ALREADY_EXISTS"})
    void updateServiceError(int httpStatus, String code) throws Exception {
        when(accountService.update(1L, updateRequest()))
                .thenThrow(new ApiException(HttpStatus.valueOf(httpStatus), code, "Errore modifica"));
        mockMvc.perform(put(BASE_URL + "/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(UPDATE_JSON))
                .andExpect(status().is(httpStatus))
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.message").value("Errore modifica"));
        verify(accountService).update(1L, updateRequest());
    }

    @ParameterizedTest
    @CsvSource({"404, ACCOUNT_NOT_FOUND",
            "409, ACCOUNT_BALANCE_NOT_ZERO",
            "409, ACCOUNT_HAS_TRANSACTION"})
    void deleteServiceError(int httpStatus, String code) throws Exception {
        doThrow(new ApiException(HttpStatus.valueOf(httpStatus), code, "Errore cancellazione"))
                .when(accountService).delete(1L);
        mockMvc.perform(delete(BASE_URL + "/1"))
                .andExpect(status().is(httpStatus))
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.message").value("Errore cancellazione"));
        verify(accountService).delete(1L);
    }

    @ParameterizedTest
    @ValueSource(strings = {"POST", "PUT"})
    void rejectsMalformedJson(String method) throws Exception {
        var request = method.equals("POST") ? post(BASE_URL) : put(BASE_URL + "/1");
        mockMvc.perform(request
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        verifyNoInteractions(accountService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"POST", "PUT"})
    void rejectsMissingBody(String method) throws Exception {
        var request = method.equals("POST") ? post(BASE_URL) : put(BASE_URL + "/1");
        mockMvc.perform(request.contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        verifyNoInteractions(accountService);
    }

    @Test
    void createNonNumericPersonId() throws Exception {
        mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                   "personId": "abc",
                                   "accountNumber": "ACC-001"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        verifyNoInteractions(accountService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"GET", "BALANCE", "PUT", "DELETE"})
    void errorNonNumericAccountId(String operation) throws Exception {
        var request = switch (operation) {
            case "GET" -> get(BASE_URL + "/abc");
            case "BALANCE" -> get(BASE_URL + "/abc/balance");
            case "PUT" -> put(BASE_URL + "/abc").contentType(MediaType.APPLICATION_JSON)
                    .content(UPDATE_JSON);
            case "DELETE" -> delete(BASE_URL + "/abc");
            default -> throw new IllegalArgumentException(operation);
        };
        mockMvc.perform(request).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
        verifyNoInteractions(accountService);
    }

    @Test
    void unexpectedInternalError() throws Exception {
        when(accountService.findAll(any(Pageable.class))).thenThrow(new RuntimeException("Errore database"));
        mockMvc.perform(get(BASE_URL))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("Errore interno durante l'operazione"));
        verify(accountService).findAll(any(Pageable.class));
    }

    @Test
    void databaseConflictReturnsConflict() throws Exception {
        when(accountService.create(createRequest()))
                .thenThrow(new org.springframework.dao.DataIntegrityViolationException("Duplicate account number"));
        mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_JSON))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DATA_CONFLICT"));
    }
}
