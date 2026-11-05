package it.russello.contocorrente.dto;

import it.russello.contocorrente.entity.Account;

import java.math.BigDecimal;

public record AccountBalanceResponse(Long accountId, BigDecimal balance, String currency) {
    public static AccountBalanceResponse from(Account account) {
        return new AccountBalanceResponse(
                account.getId(),
                account.getBalance(),
                "€ (EUR)");
    }
}
