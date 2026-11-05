package it.russello.contocorrente.dto;

import it.russello.contocorrente.entity.Account;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record AccountResponse(Long id,
                              Long personId,
                              String accountNumber,
                              LocalDateTime openedAt,
                              BigDecimal balance) {
    public static AccountResponse from(Account account) {
        return new AccountResponse(account.getId(),
                account.getPerson().getId(),
                account.getAccountNumber(),
                account.getOpenedAt(),
                account.getBalance());
    }
}
