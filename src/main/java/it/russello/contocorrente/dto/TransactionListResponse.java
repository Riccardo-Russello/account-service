package it.russello.contocorrente.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TransactionListResponse(
        Long id,
        TransactionType type,
        BigDecimal amount,
        LocalDateTime occurredAt,
        Long sourceAccountId,
        Long destinationAccountId
) {}