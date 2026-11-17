package it.russello.contocorrente.client;

import it.russello.contocorrente.dto.TransactionListResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;

import java.util.List;

@Component
@RequiredArgsConstructor
public class TransactionClient {
    private final RemoteHttpClient remoteHttpClient;

    @Value("${clients.transaction.base-url}")
    private String transactionBaseUrl;

    public boolean hasTransactions(Long id) {
        try {
            List<TransactionListResponse> transaction = remoteHttpClient.getRemoteBody(
                    transactionBaseUrl + "/api/account/{id}/transaction", new ParameterizedTypeReference<List<TransactionListResponse>>() {
                    }, id);
            return !transaction.isEmpty();
        } catch (HttpClientErrorException.NotFound ex) {
            throw remoteHttpClient.upstreamUnavailable();
        }
    }
}
