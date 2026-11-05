package it.russello.contocorrente.client;

import it.russello.contocorrente.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;

@Component
public class InternalClient {
    private final WebClient client;

    public InternalClient(WebClient.Builder builder) {
        this.client = builder.build();
    }

    public boolean check(String baseUrl, String path, Long id) {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwt)
                || !jwt.isAuthenticated()) {
            throw new AuthenticationCredentialsNotFoundException(
                    "Autenticazione JWT richiesta");
        }

        return Boolean.TRUE.equals(client.get()
                .uri(baseUrl + path, id)
                .headers(headers -> headers.setBearerAuth(jwt.getToken().getTokenValue()))
                .retrieve()
                .bodyToMono(Boolean.class)
                .switchIfEmpty(reactor.core.publisher.Mono.error(
                        new IllegalStateException("Risposta vuota dal servizio remoto")))
                .timeout(Duration.ofSeconds(3))
                .onErrorMap(error -> new ApiException(
                        HttpStatus.SERVICE_UNAVAILABLE,
                        "UPSTREAM_UNAVAILABLE",
                        "Servizio remoto non disponibile"))
                .block());
    }
}