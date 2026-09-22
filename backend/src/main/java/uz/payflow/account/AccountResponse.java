package uz.payflow.account;

import java.math.BigDecimal;
import java.time.Instant;

public record AccountResponse(Long id, String number, String name, Currency currency,
                              BigDecimal balance, Instant createdAt) {

    static AccountResponse from(Account account) {
        return new AccountResponse(account.getId(), account.getNumber(), account.getName(),
                account.getCurrency(), account.getBalance(), account.getCreatedAt());
    }
}
