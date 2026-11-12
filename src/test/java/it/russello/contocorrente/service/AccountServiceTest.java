package it.russello.contocorrente.service;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import it.russello.contocorrente.dto.PersonResponse;
import it.russello.contocorrente.dto.TransactionListResponse;
import it.russello.contocorrente.dto.TransactionType;
import it.russello.contocorrente.dto.AccountBalanceResponse;
import it.russello.contocorrente.dto.AccountCreateRequest;
import it.russello.contocorrente.dto.AccountResponse;
import it.russello.contocorrente.dto.AccountUpdateRequest;
import it.russello.contocorrente.entity.Account;
import it.russello.contocorrente.entity.Person;
import it.russello.contocorrente.exception.ApiException;
import it.russello.contocorrente.repository.AccountRepository;
import it.russello.contocorrente.repository.PersonRepository;
import it.russello.contocorrente.security.AccountSecurity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.*;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AccountServiceTest {
    private static final LocalDateTime OPENED_AT = LocalDateTime.of(2026, 1, 10, 12, 0);
    @Mock
    private AccountRepository accountRepository;
    @Mock
    private PersonRepository personRepository;
    @Mock
    private RestClient restClient;
    @Mock
    private AccountSecurity accountSecurity;
    @InjectMocks
    private AccountService accountService;

    private static final String USER_URL = "http://user.test";
    private static final String TRANSACTION_URL = "http://transaction.test";
    private static final String PERSON_BY_ID = "/api/person/{id}";
    private static final String MY_PROFILE = "/api/person/my_profile";
    private static final String ACCOUNT_TRANSACTIONS = "/api/account/{id}/transaction";

    private RestClient.RequestHeadersUriSpec<?> uriSpec;

    @BeforeEach
    void configureClientUrls() {
        uriSpec = mock(RestClient.RequestHeadersUriSpec.class);
        ReflectionTestUtils.setField(accountService, "userBaseUrl", USER_URL);
        ReflectionTestUtils.setField(accountService, "transactionBaseUrl", TRANSACTION_URL);
        var jwt = Jwt.withTokenValue("test-token")
                .header("alg", "HS256")
                .subject("test")
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(jwt, java.util.List.of()));
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    private void stubExistingOwner(Long id, Person owner, boolean admin) {
        var response = admin
                ? stubGet(USER_URL + PERSON_BY_ID, id)
                : stubGet(USER_URL + MY_PROFILE);
        when(response.body(PersonResponse.class)).thenReturn(new PersonResponse(id, null, null, null));
        when(personRepository.getReferenceById(id)).thenReturn(owner);
    }

    private static Person person() {
        Person person = new Person("Mario", "Rossi", LocalDate.of(1990, 5, 15));
        ReflectionTestUtils.setField(person, "id", 10L);
        return person;
    }

    private static Account account(Long id) {
        Account account = new Account(person(), "ACC-001");
        ReflectionTestUtils.setField(account, "id", id);
        ReflectionTestUtils.setField(account, "openedAt", OPENED_AT);
        return account;
    }

    private static AccountCreateRequest validCreateRequest() {
        return new AccountCreateRequest(10L, "ACC-001");
    }

    private static void assertApiError(ApiException exception, HttpStatus status, String code) {
        assertEquals(status, exception.getStatus());
        assertEquals(code, exception.getCode());
    }

    @Test
    void createAccount() {
        Person owner = person();
        Account saved = account(1L);

        stubExistingOwner(10L, owner, true);
        when(accountRepository.existsByAccountNumber("ACC-001")).thenReturn(false);
        when(accountRepository.saveAndFlush(any(Account.class))).thenReturn(saved);
        when(accountSecurity.currentPersonId()).thenReturn(10L);
        when(accountSecurity.isAdmin()).thenReturn(true);
        AccountResponse response = accountService.create(validCreateRequest());
        assertEquals(new AccountResponse(1L, 10L, "ACC-001", OPENED_AT, new BigDecimal("0.00")), response);
        ArgumentCaptor<Account> captor = ArgumentCaptor.forClass(Account.class);
        verify(accountRepository).saveAndFlush(captor.capture());

        Account submitted = captor.getValue();
        assertNull(submitted.getId());
        assertSame(owner, submitted.getPerson());
        assertEquals("ACC-001", submitted.getAccountNumber());
        assertEquals(new BigDecimal("0.00"), submitted.getBalance());
        assertNotNull(submitted.getOpenedAt());
        assertEquals(0, submitted.getOpenedAt().getNano());

        verify(uriSpec, never()).uri(
                eq(TRANSACTION_URL + ACCOUNT_TRANSACTIONS), anyLong());
    }

    @Test
    void createWithoutPersonIdUsesCurrentPerson() {
        when(accountSecurity.currentPersonId()).thenReturn(10L);
        when(accountSecurity.isAdmin()).thenReturn(false);
        stubExistingOwner(10L, person(), false);
        when(accountRepository.saveAndFlush(any(Account.class))).thenAnswer((invocation -> invocation.getArgument(0)));
        AccountResponse response = accountService.create(new AccountCreateRequest(null, "ACC-SELF"));
        assertEquals(10L, response.personId());
        assertEquals("ACC-SELF", response.accountNumber());
        verify(personRepository).getReferenceById(10L);
    }

    @Test
    void userCreateAccountForSelf() {
        when(accountSecurity.currentPersonId()).thenReturn(10L);
        when(accountSecurity.isAdmin()).thenReturn(false);
        stubExistingOwner(10L, person(), false);
        when(accountRepository.saveAndFlush(any(Account.class))).thenAnswer((invocation -> invocation.getArgument(0)));
        AccountResponse response = accountService.create(validCreateRequest());
        assertEquals(10L, response.personId());
        verify(accountRepository).saveAndFlush(any(Account.class));
    }

    @Test
    void userCannotCreateAccountForAnotherPerson() {
        when(accountSecurity.currentPersonId()).thenReturn(10L);
        when(accountSecurity.isAdmin()).thenReturn(false);
        assertThrows(AccessDeniedException.class, () -> accountService.create(new AccountCreateRequest(20L, "ACC-OTHER")));
        verifyNoInteractions(personRepository, accountRepository, restClient);
    }

    @Test
    void adminCanCreateAccountForAnotherPerson() {
        Person other = person();
        ReflectionTestUtils.setField(other, "id", 20L);
        when(accountSecurity.currentPersonId()).thenReturn(10L);
        when(accountSecurity.isAdmin()).thenReturn(true);
        stubExistingOwner(20L, other, true);
        when(accountRepository.saveAndFlush(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));
        AccountResponse response = accountService.create(new AccountCreateRequest(20L, "ACC-OTHER"));
        assertEquals(20L, response.personId());
    }

    @Test
    void createMissingPerson() {
        when(stubGet(USER_URL + PERSON_BY_ID, 10L).body(PersonResponse.class)).thenThrow(HttpClientErrorException.create(
                        HttpStatus.NOT_FOUND, "Not Found", HttpHeaders.EMPTY, new byte[0], null));
        when(accountSecurity.currentPersonId()).thenReturn(10L);
        when(accountSecurity.isAdmin()).thenReturn(true);
        ApiException exception = assertThrows(ApiException.class, () -> accountService.create(validCreateRequest()));
        assertApiError(exception, HttpStatus.NOT_FOUND, "PERSON_NOT_FOUND");
        verifyNoInteractions(accountRepository, personRepository);
    }

    @Test
    void createDuplicateAccountNumber() {
        stubExistingOwner(10L, person(), true);
        when(accountRepository.existsByAccountNumber("ACC-001")).thenReturn(true);
        when(accountSecurity.currentPersonId()).thenReturn(10L);
        when(accountSecurity.isAdmin()).thenReturn(true);
        ApiException exception = assertThrows(ApiException.class, () -> accountService.create(validCreateRequest()));
        assertApiError(exception, HttpStatus.CONFLICT, "ACCOUNT_NUMBER_ALREADY_EXISTS");
        verify(accountRepository, never()).saveAndFlush(any(Account.class));
        verify(uriSpec, never()).uri(
                eq(TRANSACTION_URL + ACCOUNT_TRANSACTIONS), anyLong());
    }

    @Test
    void createAccountNumberWith30Characters() {
        String number = "a".repeat(30);
        stubExistingOwner(10L, person(), true);
        when(accountRepository.saveAndFlush(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(accountSecurity.currentPersonId()).thenReturn(10L);
        when(accountSecurity.isAdmin()).thenReturn(true);
        AccountResponse response = accountService.create(new AccountCreateRequest(10L, number));
        assertEquals(number, response.accountNumber());
        verify(accountRepository).existsByAccountNumber(number);
        verify(accountRepository).saveAndFlush(any(Account.class));
    }

    @Test
    void findAllAccounts() {
        Account first = account(1L);
        Account second = account(2L);
        second.setAccountNumber("ACC-002");
        second.setBalance(new BigDecimal("50.00"));
        Pageable pageable = PageRequest.of(0, 10, Sort.by("id"));
        when(accountSecurity.isAdmin()).thenReturn(true);
        when(accountRepository.findAll(pageable)).thenReturn(new PageImpl<>(List.of(first, second), pageable, 2));
        Page<AccountResponse> responses = accountService.findAll(pageable, null);
        assertEquals(List.of(new AccountResponse(1L, 10L, "ACC-001", OPENED_AT, new BigDecimal("0.00")),
                        new AccountResponse(2L, 10L, "ACC-002", OPENED_AT, new BigDecimal("50.00"))),
                responses.getContent());
        assertEquals(0, responses.getNumber());
        assertEquals(10, responses.getSize());
        assertEquals(2L, responses.getTotalElements());
        assertEquals(1, responses.getTotalPages());
        verify(accountRepository).findAll(pageable);
        verify(accountRepository, never()).findByPerson_Id(anyLong(), any(Pageable.class));
    }

    @Test
    void userFindAllReturnOwnAccounts() {
        Pageable pageable = PageRequest.of(0, 10, Sort.by("id"));
        Account ownAccount = account(1L);
        when(accountSecurity.isAdmin()).thenReturn(false);
        when(accountSecurity.currentPersonId()).thenReturn(10L);
        when(accountRepository.findByPerson_Id(10L, pageable)).thenReturn(new PageImpl<>(List.of(ownAccount), pageable, 1));
        Page<AccountResponse> responses = accountService.findAll(pageable, null);
        assertEquals(List.of(AccountResponse.from(ownAccount)), responses.getContent());
        assertEquals(1L, responses.getTotalElements());
        assertEquals(1, responses.getTotalPages());
        verify(accountRepository).findByPerson_Id(10L, pageable);
        verify(accountRepository, never()).findAll(any(Pageable.class));
    }

    @Test
    void userWithoutAccountGetsEmptyList() {
        Pageable pageable = PageRequest.of(0, 10, Sort.by("id"));
        when(accountSecurity.isAdmin()).thenReturn(false);
        when(accountSecurity.currentPersonId()).thenReturn(10L);
        when(accountRepository.findByPerson_Id(10L, pageable)).thenReturn(Page.empty(pageable));
        Page<AccountResponse> responses = accountService.findAll(pageable, null);
        assertTrue(responses.isEmpty());
        assertEquals(0L, responses.getTotalElements());
        assertEquals(0, responses.getTotalPages());
        verify(accountRepository).findByPerson_Id(10L, pageable);
        verify(accountRepository, never()).findAll(any(Pageable.class));
    }

    @Test
    void findById() {
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account(1L)));
        assertEquals(new AccountResponse(1L, 10L, "ACC-001", OPENED_AT, new BigDecimal("0.00")),
                accountService.findById(1L));
        verify(accountRepository).findById(1L);
    }

    @Test
    void getBalanceAndCurrency() {
        Account existing = account(1L);
        existing.setBalance(new BigDecimal("125.50"));
        when(accountRepository.findById(1L)).thenReturn(Optional.of(existing));
        assertEquals(new AccountBalanceResponse(1L, new BigDecimal("125.50"), "€ (EUR)"),
                accountService.getBalance(1L));
        verify(accountRepository).findById(1L);
    }

    @Test
    void updateExistingAccount() {
        Account existing = account(1L);
        existing.setBalance(new BigDecimal("50.00"));
        Person originalOwner = existing.getPerson();

        when(accountRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(existing));
        when(accountRepository.existsByAccountNumberAndIdNot("ACC-002", 1L)).thenReturn(false);
        when(accountRepository.saveAndFlush(existing)).thenReturn(existing);

        AccountResponse response = accountService.update(1L, new AccountUpdateRequest("ACC-002"));
        assertEquals(new AccountResponse(1L, 10L, "ACC-002", OPENED_AT, new BigDecimal("50.00")),
                response);
        assertEquals("ACC-002", existing.getAccountNumber());
        assertSame(originalOwner, existing.getPerson());
        assertEquals(OPENED_AT, existing.getOpenedAt());
        assertEquals(new BigDecimal("50.00"), existing.getBalance());
        verify(accountRepository).existsByAccountNumberAndIdNot("ACC-002", 1L);
        verify(accountRepository).saveAndFlush(existing);
        verifyNoInteractions(personRepository, restClient);
    }

    @Test
    void updateDuplicateAccountNumber() {
        Account existing = account(1L);
        when(accountRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(existing));
        when(accountRepository.existsByAccountNumberAndIdNot("ACC-002", 1L)).thenReturn(true);
        ApiException exception = assertThrows(ApiException.class, () -> accountService.update(1L, new AccountUpdateRequest("ACC-002")));
        assertApiError(exception, HttpStatus.CONFLICT, "ACCOUNT_NUMBER_ALREADY_EXISTS");
        assertEquals("ACC-001", existing.getAccountNumber());
        verify(accountRepository, never()).saveAndFlush(any(Account.class));
    }

    @Test
    void updateAccountNumberWith30Characters() {
        String number = "a".repeat(30);
        Account existing = account(1L);
        existing.setAccountNumber(number);
        when(accountRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(existing));
        when(accountRepository.existsByAccountNumberAndIdNot(number, 1L)).thenReturn(false);
        when(accountRepository.saveAndFlush(existing)).thenReturn(existing);
        AccountResponse response = accountService.update(1L, new AccountUpdateRequest(number));
        assertEquals(number, response.accountNumber());
        verify(accountRepository).existsByAccountNumberAndIdNot(number, 1L);
        verify(accountRepository).saveAndFlush(existing);
    }

    @Test
    void deleteValidAccount() {
        Account existing = account(1L);
        when(accountRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(existing));
        when(stubGet(TRANSACTION_URL + ACCOUNT_TRANSACTIONS, 1L)
                .body(new ParameterizedTypeReference<List<TransactionListResponse>>() {}))
                .thenReturn(List.of());
        accountService.delete(1L);
        var order = inOrder(accountRepository, restClient);
        order.verify(accountRepository).findByIdForUpdate(1L);
        order.verify(restClient).get();
        order.verify(accountRepository).delete(existing);
        order.verify(accountRepository).flush();
        verifyNoInteractions(personRepository);
    }

    @ParameterizedTest
    @ValueSource(strings = {"10.00", "-10.00"})
    void deleteAccountWithBalance(String balance) {
        Account existing = account(1L);
        existing.setBalance(new BigDecimal(balance));
        when(accountRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(existing));
        ApiException exception = assertThrows(ApiException.class, () -> accountService.delete(1L));
        assertApiError(exception, HttpStatus.CONFLICT, "ACCOUNT_BALANCE_NOT_ZERO");
        verify(accountRepository, never()).delete(any(Account.class));
        verify(accountRepository, never()).flush();
        verifyNoInteractions(personRepository, restClient);
    }

    @Test
    void deleteAccountWithTransactions() {
        when(accountRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(account(1L)));
        when(stubGet(TRANSACTION_URL + ACCOUNT_TRANSACTIONS, 1L)
                .body(new ParameterizedTypeReference<List<TransactionListResponse>>() {}))
                .thenReturn(List.of(new TransactionListResponse(
                        1L, TransactionType.DEPOSIT, BigDecimal.ONE, null, 1L, null)));
        ApiException exception = assertThrows(ApiException.class, () -> accountService.delete(1L));
        assertApiError(exception, HttpStatus.CONFLICT, "ACCOUNT_HAS_TRANSACTION");
        verify(accountRepository, never()).delete(any(Account.class));
        verify(accountRepository, never()).flush();
    }

    @ParameterizedTest
    @ValueSource(strings = {"findById", "getBalance", "update", "delete"})
    void operationsMissingAccount(String operation) {
        boolean write = operation.equals("update") || operation.equals("delete");
        if (write) {
            when(accountRepository.findByIdForUpdate(1L)).thenReturn(Optional.empty());
        } else {
            when(accountRepository.findById(1L)).thenReturn(Optional.empty());
        }
        ApiException exception = assertThrows(ApiException.class, () -> {
            switch (operation) {
                case "findById" -> accountService.findById(1L);
                case "getBalance" -> accountService.getBalance(1L);
                case "update" -> accountService.update(1L, new AccountUpdateRequest("ACC-002"));
                case "delete" -> accountService.delete(1L);
                default -> throw new IllegalArgumentException(operation);
            }
        });
        assertApiError(exception, HttpStatus.NOT_FOUND, "ACCOUNT_NOT_FOUND");
        if (write) {
            verify(accountRepository).findByIdForUpdate(1L);
        } else {
            verify(accountRepository).findById(1L);
        }
        verifyNoMoreInteractions(accountRepository);
        verifyNoInteractions(personRepository, restClient);
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
