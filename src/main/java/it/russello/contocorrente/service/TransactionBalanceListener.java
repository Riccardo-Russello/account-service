package it.russello.contocorrente.service;

import org.springframework.messaging.handler.annotation.Header;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.stereotype.Component;
import org.springframework.util.backoff.FixedBackOff;

import java.math.BigDecimal;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.kafka.consumer-enabled", havingValue = "true")
public class TransactionBalanceListener {
    private final JdbcTemplate jdbc;

    @KafkaListener(topics = "${app.kafka.transaction-topic}")
    public void updateBalance(@Header(KafkaHeaders.RECEIVED_KEY) String accountId, String delta) {
        jdbc.update(
                "UPDATE account SET balance = balance + ? WHERE id = ?",
                new BigDecimal(delta), Long.parseLong(accountId));
    }

    @Bean
    DefaultErrorHandler transactionKafkaErrorHandler() {
        return new DefaultErrorHandler(
                new FixedBackOff(1000L, FixedBackOff.UNLIMITED_ATTEMPTS));
    }
}