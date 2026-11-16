package it.russello.contocorrente.client;

import it.russello.contocorrente.exception.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.function.Function;

@Component
@RequiredArgsConstructor
public class RemoteHttpClient {
    private final RestClient restClient;

    <T> T getRemoteBody(String url, Class<T> bodyType, Object... uriVariables) {
        return readRemoteBody(url, response -> response.body(bodyType), uriVariables);
    }

    <T> T getRemoteBody(
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

    ApiException upstreamUnavailable() {
        return new ApiException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "UPSTREAM_UNAVAILABLE",
                "Servizio remoto non disponibile");
    }

}