package it.russello.contocorrente.service;

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
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.parameters.P;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import java.util.List;
import java.util.Map;
import org.springframework.web.client.HttpClientErrorException;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class AccountService {
    private final AccountRepository accountRepository;
    private final PersonRepository personRepository;
    private final AccountSecurity accountSecurity;
    private final RestClient restClient;

    @Value("${clients.user.base-url}")
    private String userBaseUrl;

    @Value("${clients.transaction.base-url}")
    private String transactionBaseUrl;

    @PreAuthorize("hasAnyRole('admin', 'user')")
    public AccountResponse create(AccountCreateRequest request) {
        Long currentPersonId = accountSecurity.currentPersonId();
        Long ownerPersonId = request.personId() == null ? currentPersonId : request.personId();
        if (!accountSecurity.isAdmin() && !currentPersonId.equals(ownerPersonId)) {
            throw new AccessDeniedException("Puoi creare un conto soltanto per te stesso");
        }
        if (!personExists(ownerPersonId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "PERSON_NOT_FOUND", "Persona non trovata");
        }
        Person person = personRepository.getReferenceById(ownerPersonId);

        if (accountRepository.existsByAccountNumber(request.accountNumber())) {
            throw duplicateAccountNumber();
        }
        Account account = new Account(person, request.accountNumber());
        Account savedAccount = accountRepository.saveAndFlush(account);
        return AccountResponse.from(savedAccount);
    }

    @PreAuthorize("hasAnyRole('admin', 'user')")
    @Transactional(readOnly = true)
    public Page<AccountResponse> findAll(Pageable pageable, Long personId) {
        Long ownerId = accountSecurity.isAdmin()
                ? personId
                : accountSecurity.currentPersonId();
        Page<Account> accounts = ownerId == null
                ? accountRepository.findAll(pageable)
                : accountRepository.findByPerson_Id(ownerId, pageable);
        return accounts.map(AccountResponse::from);
    }

    @PreAuthorize("""
            hasRole('admin') or
            (hasRole('user') and @accountSecurity.isOwner(#id, authentication))
            """)
    public AccountResponse findById(Long id) {
        return AccountResponse.from(requireAccount(id));
    }

    @PreAuthorize("""
            hasRole('admin') or (hasRole('user') and @accountSecurity.isOwner(#id, authentication))
            """)
    public AccountBalanceResponse getBalance(@P("id") Long id) {
        Account account = requireAccount(id);
        return AccountBalanceResponse.from(account);
    }

    @PreAuthorize("hasRole('admin')")
    @Transactional
    public AccountResponse update(Long id, AccountUpdateRequest request) {
        Account account = requireAccountForUpdate(id);
        if (accountRepository.existsByAccountNumberAndIdNot(
                request.accountNumber(), id)) {
            throw duplicateAccountNumber();
        }
        account.setAccountNumber(request.accountNumber());
        Account savedAccount = accountRepository.saveAndFlush(account);
        return AccountResponse.from(savedAccount);
    }

    @PreAuthorize("hasRole('admin')")
    @Transactional
    public void delete(Long id) {
        Account account = requireAccountForUpdate(id);
        if (account.getBalance().compareTo(BigDecimal.ZERO) != 0) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "ACCOUNT_BALANCE_NOT_ZERO",
                    "Conto può essere eliminato solo con saldo 0");
        }
        if (hasTransactions(id)) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "ACCOUNT_HAS_TRANSACTION",
                    "Il conto ha movimenti associati e non può essere eliminato");
        }
        accountRepository.delete(account);
        accountRepository.flush();
    }


    private Account requireAccount(Long id) {
        return accountRepository.findById(id)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "ACCOUNT_NOT_FOUND",
                        "Conto non trovato"
                ));
    }

    private ApiException duplicateAccountNumber() {
        return new ApiException(HttpStatus.CONFLICT,
                "ACCOUNT_NUMBER_ALREADY_EXISTS",
                "Numero di conto già utilizzato");
    }

    private Account requireAccountForUpdate(Long id) {
        return accountRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND, "ACCOUNT_NOT_FOUND", "Conto non trovato"));
    }

    private <T> T getRemoteBody(String url, Class<T> bodyType, Object... uriVariables) {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwt)
                || !jwt.isAuthenticated()) {
            throw new AuthenticationCredentialsNotFoundException(
                    "Autenticazione JWT richiesta");
        }

        try {
            T result = restClient.get()
                    .uri(url, uriVariables)
                    .headers(headers -> headers.setBearerAuth(jwt.getToken().getTokenValue()))
                    .retrieve()
                    .body(bodyType);
            if (result != null) {
                return result;
            }
        } catch (HttpClientErrorException.NotFound ex) {
        throw ex;
        } catch (RestClientException ex) {
            throw upstreamUnavailable();
        }

        throw upstreamUnavailable();
    }

    private ApiException upstreamUnavailable() {
        return new ApiException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "UPSTREAM_UNAVAILABLE",
                "Servizio remoto non disponibile");
    }

    private boolean personExists(Long id) {
        try {
            Map<?, ?> person = accountSecurity.isAdmin()
                    ? getRemoteBody(userBaseUrl + "/api/person/{id}", Map.class, id)
                    : getRemoteBody(userBaseUrl + "/api/person/my_profile", Map.class);

            if (person.get("id") instanceof Number personId) {
                return id.equals(personId.longValue());
            }
            throw upstreamUnavailable();
        } catch (HttpClientErrorException.NotFound ex) {
            return false;
        }
    }

    private boolean hasTransactions(Long id) {
        try {
            return !getRemoteBody(
                    transactionBaseUrl + "/api/account/{id}/transaction", List.class, id)
                    .isEmpty();
        } catch (HttpClientErrorException.NotFound ex) {
            throw upstreamUnavailable();
        }
    }

}
