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
                JOIN accounttransaction t
                    ON a.id IN (t.account_id, t.destination_account_id)
                SET a.balance = a.booked_balance
                WHERE t.id = ?
                """, Long.parseLong(transactionId));
    }

    @Bean
    DefaultErrorHandler transactionKafkaErrorHandler() {
        return new DefaultErrorHandler(
                new FixedBackOff(1000L, FixedBackOff.UNLIMITED_ATTEMPTS));
    }
}