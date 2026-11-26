package it.russello.contocorrente.service;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.stereotype.Component;
import org.springframework.util.backoff.FixedBackOff;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.kafka.consumer-enabled", havingValue = "true")
public class TransactionBalanceListener {
    private final JdbcTemplate jdbc;

    @KafkaListener(topics = "${app.kafka.transaction-topic}")
    public void updateBalance(String transactionId) {
        jdbc.update("""
                UPDATE account a
                JOIN accounttransaction event
                  ON a.id IN (event.account_id, event.destination_account_id)
                SET a.balance = (
                    SELECT COALESCE(SUM(CASE
                        WHEN movement.destination_account_id = a.id THEN movement.amount
                        WHEN movement.type = 'DEPOSIT' THEN movement.amount
                        ELSE -movement.amount
                    END), 0.00)
                    FROM accounttransaction movement
                    WHERE movement.account_id = a.id
                       OR movement.destination_account_id = a.id
                )
                WHERE event.id = ?
                """, Long.parseLong(transactionId));
    }

    @Bean
    DefaultErrorHandler transactionKafkaErrorHandler() {
        return new DefaultErrorHandler(
                new FixedBackOff(1000L, FixedBackOff.UNLIMITED_ATTEMPTS));
    }
}