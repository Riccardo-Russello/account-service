package it.russello.contocorrente.controller;

import org.springframework.web.client.RestClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.jayway.jsonpath.JsonPath;
import it.russello.contocorrente.entity.Account;
import it.russello.contocorrente.entity.Person;
import it.russello.contocorrente.repository.AccountRepository;
import it.russello.contocorrente.repository.PersonRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static it.russello.contocorrente.support.TestJwt.admin;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
public class AccountControllerIT {
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private AccountRepository accountRepository;
    @Autowired
    private PersonRepository personRepository;
    @Autowired
    private EntityManager entityManager;
    @MockitoBean
    private RestClient restClient;

    private Person person;

    private RestClient.RequestHeadersUriSpec<?> uriSpec;

    @BeforeEach
    void setUp() {
        uriSpec = mock(RestClient.RequestHeadersUriSpec.class);
        person = new Person("Mario", "Rossi", LocalDate.of(1990, 5, 15));
        person.setUsername("mario.rossi");
        person.setPassword("passwmario");
        personRepository.saveAndFlush(person);
        when(stubGet("http://user.test/api/person/{id}", person.getId()).body(Map.class)).thenReturn(Map.of("id", person.getId()));
    }

    @Test
    void createAccount() throws Exception {
        long counterBefore = accountRepository.count();
        var response = mockMvc.perform(post("/api/account").with(admin(person.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "personId" : %d,
                                    "accountNumber" : "ACC-001"
                                }
                                """.formatted(person.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.personId").value(person.getId()))
                .andExpect(jsonPath("$.accountNumber").value("ACC-001"))
                .andExpect(jsonPath("$.openedAt").isNotEmpty())
                .andExpect(jsonPath("$.balance").value(0.0))
                .andReturn();
        entityManager.flush();
        entityManager.clear();

        Number id = JsonPath.read(response.getResponse().getContentAsString(), "$.id");
        Account saved = accountRepository.findById(id.longValue()).orElseThrow();
        assertEquals(counterBefore + 1, accountRepository.count());
        assertEquals(person.getId(), saved.getPerson().getId());
        assertEquals("ACC-001", saved.getAccountNumber());
        assertNotNull(saved.getOpenedAt());
        assertEquals(0, saved.getBalance().compareTo(BigDecimal.ZERO));
    }

    @Test
    void createAccountForMissingPerson() throws Exception {
        var response = stubGet("http://user.test/api/person/{id}", Long.MAX_VALUE);
        when(response.body(Map.class)).thenThrow(HttpClientErrorException.create(
                HttpStatus.NOT_FOUND, "Not Found", HttpHeaders.EMPTY, new byte[0], null));

        long before = accountRepository.count();
        mockMvc.perform(post("/api/account")
                        .with(admin(person.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "personId":%d,
                                  "accountNumber":"MISSING-PERSON"
                                }
                                """.formatted(Long.MAX_VALUE)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PERSON_NOT_FOUND"));
        assertEquals(before, accountRepository.count());
    }

    @ParameterizedTest
    @ValueSource(strings = {"POST", "PUT"})
    void duplicateAccountNumberIsRejected(String method) throws Exception {
        accountRepository.saveAndFlush(new Account(person, "TAKEN"));
        Account editable = accountRepository.saveAndFlush(
                new Account(person, "ORIGINAL"));
        long before = accountRepository.count();
        var request = method.equals("POST")
                ? post("/api/account")
                : put("/api/account/{id}", editable.getId());
        mockMvc.perform(request
                        .with(admin(person.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"accountNumber":"TAKEN"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code")
                        .value("ACCOUNT_NUMBER_ALREADY_EXISTS"));
        entityManager.clear();
        assertEquals(before, accountRepository.count());
        assertEquals("ORIGINAL",
                accountRepository.findById(editable.getId())
                        .orElseThrow().getAccountNumber());
    }

    @Test
    void findAllAccounts() throws Exception {
        Account account = accountRepository.saveAndFlush(new Account(person, "ACC-001"));
        entityManager.clear();
        mockMvc.perform(get("/api/account").with(admin(person.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(account.getId()))
                .andExpect(jsonPath("$.content[0].personId").value(person.getId()))
                .andExpect(jsonPath("$.content[0].accountNumber").value("ACC-001"))
                .andExpect(jsonPath("$.content[0].openedAt").isNotEmpty())
                .andExpect(jsonPath("$.content[0].balance").value(0.0))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1));
    }

    @Test
    void findAllPaginatesAndSortsAccounts() throws Exception {
        accountRepository.saveAndFlush(new Account(person, "ACC-001"));
        accountRepository.saveAndFlush(new Account(person, "ACC-002"));
        accountRepository.saveAndFlush(new Account(person, "ACC-003"));
        entityManager.clear();
        mockMvc.perform(get("/api/account")
                        .with(admin(person.getId()))
                        .param("page", "1")
                        .param("size", "1")
                        .param("sort", "accountNumber,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].accountNumber").value("ACC-002"))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(3));
        mockMvc.perform(get("/api/account")
                        .with(admin(person.getId()))
                        .param("page", "3")
                        .param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.page").value(3))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(3));
    }

    @Test
    void findAccountById() throws Exception {
        Account account = accountRepository.saveAndFlush(new Account(person, "ACC-001"));
        entityManager.clear();
        mockMvc.perform(get("/api/account/{id}", account.getId()).with(admin(person.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(account.getId()))
                .andExpect(jsonPath("$.personId").value(person.getId()))
                .andExpect(jsonPath("$.accountNumber").value("ACC-001"))
                .andExpect(jsonPath("$.openedAt").isNotEmpty())
                .andExpect(jsonPath("$.balance").value(0.0));
    }

    @Test
    void getAccountBalance() throws Exception {
        Account account = new Account(person, "ACC-001");
        account.setBalance(new BigDecimal("125.50"));
        accountRepository.saveAndFlush(account);
        entityManager.clear();
        mockMvc.perform(get("/api/account/{id}/balance", account.getId()).with(admin(person.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value(account.getId()))
                .andExpect(jsonPath("$.balance").value(125.50))
                .andExpect(jsonPath("$.currency").value("€ (EUR)"));
    }

    @Test
    void updateAccount() throws Exception {
        Account account = accountRepository.saveAndFlush(new Account(person, "ACC-001"));
        Long id = account.getId();
        long counterBefore = accountRepository.count();
        entityManager.clear();
        mockMvc.perform(put("/api/account/{id}", id).with(admin(person.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "accountNumber": "ACC-002"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.personId").value(person.getId()))
                .andExpect(jsonPath("$.accountNumber").value("ACC-002"))
                .andExpect(jsonPath("$.balance").value(0.0));
        entityManager.flush();
        entityManager.clear();
        Account updated = accountRepository.findById(id).orElseThrow();
        assertEquals(counterBefore, accountRepository.count());
        assertEquals("ACC-002", updated.getAccountNumber());
        assertEquals(person.getId(), updated.getPerson().getId());
        assertEquals(account.getOpenedAt(), updated.getOpenedAt());
        assertEquals(0, updated.getBalance().compareTo(account.getBalance()));
    }

    @Test
    void deleteAccount() throws Exception {
        Account account = accountRepository.saveAndFlush(new Account(person, "ACC-001"));
        Long id = account.getId();
        long counterBefore = accountRepository.count();
        entityManager.clear();
        when(stubGet("http://transaction.test/api/account/{id}/transaction", id).body(List.class)).thenReturn(List.of());

        mockMvc.perform(delete("/api/account/{id}", id).with(admin(person.getId())))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
        entityManager.flush();
        entityManager.clear();
        assertFalse(accountRepository.existsById(id));
        assertEquals(counterBefore - 1, accountRepository.count());
        assertTrue(personRepository.existsById(person.getId()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"GET", "BALANCE", "PUT", "DELETE"})
    void missingAccountReturnsNotFound(String operation) throws Exception {
        var request = switch (operation) {
            case "GET" -> get("/api/account/{id}", Long.MAX_VALUE);
            case "BALANCE" -> get("/api/account/{id}/balance", Long.MAX_VALUE);
            case "PUT" -> put("/api/account/{id}", Long.MAX_VALUE)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"accountNumber":"UPDATED"}
                            """);
            case "DELETE" -> delete("/api/account/{id}", Long.MAX_VALUE);
            default -> throw new IllegalArgumentException(operation);
        };
        long before = accountRepository.count();
        mockMvc.perform(request.with(admin(person.getId())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ACCOUNT_NOT_FOUND"));
        assertEquals(before, accountRepository.count());
    }

    @Test
    void accountWithBalanceCannotBeDeleted() throws Exception {
        Account account = new Account(person, "NONZERO");
        account.setBalance(new BigDecimal("10.00"));
        accountRepository.saveAndFlush(account);
        mockMvc.perform(delete("/api/account/{id}", account.getId())
                        .with(admin(person.getId())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ACCOUNT_BALANCE_NOT_ZERO"));
        entityManager.clear();
        Account unchanged = accountRepository.findById(account.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("10.00").compareTo(unchanged.getBalance()));
    }


    @Test
    void accountWithTransactionsCannotBeDeleted() throws Exception {
        Account account = accountRepository.saveAndFlush(new Account(person, "WITH-MOVEMENTS"));
        when(stubGet("http://transaction.test/api/account/{id}/transaction", account.getId()).body(List.class)).thenReturn(List.of(Map.of("id", 1L)));

        mockMvc.perform(delete("/api/account/{id}", account.getId())
                        .with(admin(person.getId())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ACCOUNT_HAS_TRANSACTION"));

        entityManager.clear();
        assertTrue(accountRepository.existsById(account.getId()));
    }

    private RestClient.ResponseSpec stubGet(String url, Object... uriVariables) {
        doReturn(uriSpec).when(restClient).get();
        RestClient.RequestHeadersSpec<?> request = mock(RestClient.RequestHeadersSpec.class);
        RestClient.ResponseSpec response = mock(RestClient.ResponseSpec.class);
        doReturn(request).when(uriSpec).uri(url, uriVariables);
        doReturn(request).when(request).headers(any());
        when(request.retrieve()).thenReturn(response);
        return response;
    }
}
