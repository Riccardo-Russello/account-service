package it.russello.contocorrente.client;

import it.russello.contocorrente.dto.PersonResponse;
import it.russello.contocorrente.dto.TransactionListResponse;
import it.russello.contocorrente.exception.ApiException;
import it.russello.contocorrente.security.AccountSecurity;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.function.Function;

@Component
@RequiredArgsConstructor
public class AccountDependenciesClient {
    private final RestClient restClient;
    private final AccountSecurity accountSecurity;

    @Value("${clients.user.base-url}")
    private String userBaseUrl;

    @Value("${clients.transaction.base-url}")
    private String transactionBaseUrl;

    public boolean personExists(Long id) {
        try {
            PersonResponse person = accountSecurity.isAdmin()
                    ? getRemoteBody(userBaseUrl + "/api/person/{id}", PersonResponse.class, id)
                    : getRemoteBody(userBaseUrl + "/api/person/my_profile", PersonResponse.class);

            if (person.id() == null) {
                throw upstreamUnavailable();
            }
            return id.equals(person.id());
        } catch (HttpClientErrorException.NotFound ex) {
            return false;
        }
    }

    public boolean hasTransactions(Long id) {
        try {
            List<TransactionListResponse> transactions = getRemoteBody(
                    transactionBaseUrl + "/api/account/{id}/transaction",
                    new ParameterizedTypeReference<List<TransactionListResponse>>() {},
                    id);
            return !transactions.isEmpty();
        } catch (HttpClientErrorException.NotFound ex) {
            throw upstreamUnavailable();
        }
    }

    private <T> T getRemoteBody(String url, Class<T> bodyType, Object... uriVariables) {
        return readRemoteBody(url, response -> response.body(bodyType), uriVariables);
    }

    private <T> T getRemoteBody(
            String url, ParameterizedTypeReference<T> bodyType, Object... uriVariables) {
        return readRemoteBody(url, response -> response.body(bodyType), uriVariables);
    }

    private <T> T readRemoteBody(
            String url, Function<RestClient.ResponseSpec, T> readBody, Object... uriVariables) {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwt)
                || !jwt.isAuthenticated()) {
            throw new AuthenticationCredentialsNotFoundException(
                    "Autenticazione JWT richiesta");
        }

        try {
            RestClient.ResponseSpec response = restClient.get()
                    .uri(url, uriVariables)
                    .headers(headers -> headers.setBearerAuth(jwt.getToken().getTokenValue()))
                    .retrieve();

            T result = readBody.apply(response);
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

}