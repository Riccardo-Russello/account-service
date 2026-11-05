package it.russello.contocorrente.client;

import it.russello.contocorrente.exception.ApiException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InternalClientTest {
    private static final String BASE_URL = "http://remote.test";
    private static final String PATH = "/internal/check/{id}";

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void forwardsBearerAndReadsResponse(boolean expected) {
        authenticate();
        var client = new InternalClient(WebClient.builder().exchangeFunction(request -> {
            assertEquals("http://remote.test/internal/check/42", request.url().toString());
            assertEquals("Bearer token-di-test",
                    request.headers().getFirst(HttpHeaders.AUTHORIZATION));
            return Mono.just(ClientResponse.create(HttpStatus.OK)
                    .header(HttpHeaders.CONTENT_TYPE, "application/json")
                    .body(Boolean.toString(expected))
                    .build());
        }));

        assertEquals(expected, client.check(BASE_URL, PATH, 42L));
    }

    @ParameterizedTest
    @ValueSource(ints = {204, 500})
    void mapsEmptyOrFailedResponseTo503(int statusCode) {
        authenticate();
        var client = new InternalClient(WebClient.builder().exchangeFunction(request ->
                Mono.just(ClientResponse.create(HttpStatus.valueOf(statusCode)).build())));

        var error = assertThrows(ApiException.class, () -> client.check(BASE_URL, PATH, 42L));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, error.getStatus());
        assertEquals("UPSTREAM_UNAVAILABLE", error.getCode());
    }

    @Test
    void requiresJwtAuthentication() {
        var client = new InternalClient(WebClient.builder());

        assertThrows(AuthenticationCredentialsNotFoundException.class,
                () -> client.check(BASE_URL, PATH, 42L));
    }

    private void authenticate() {
        var jwt = Jwt.withTokenValue("token-di-test")
                .header("alg", "HS256")
                .subject("test.user")
                .claim("personId", "42")
                .build();
        SecurityContextHolder.getContext()
                .setAuthentication(new JwtAuthenticationToken(jwt, List.of()));
    }
}
