package it.russello.contocorrente.client;

import it.russello.contocorrente.dto.PersonResponse;
import it.russello.contocorrente.security.AccountSecurity;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;


@Component
@RequiredArgsConstructor
public class UserClient {
    private final RemoteHttpClient remoteHttpClient;
    private final AccountSecurity accountSecurity;

    @Value("${clients.user.base-url}")
    private String userBaseUrl;

    public boolean personExists(Long id) {
        try {
            PersonResponse person = accountSecurity.isAdmin()
                    ? remoteHttpClient.getRemoteBody(userBaseUrl + "/api/person/{id}", PersonResponse.class, id)
                    : remoteHttpClient.getRemoteBody(userBaseUrl + "/api/person/my_profile", PersonResponse.class);
            if (person.id() == null) {
                throw remoteHttpClient.upstreamUnavailable();
            }
            return id.equals(person.id());
        } catch (HttpClientErrorException.NotFound ex) {
            return false;
        }
    }
}
