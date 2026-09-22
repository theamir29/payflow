package uz.payflow.account;

import java.security.SecureRandom;

import org.springframework.stereotype.Component;

/**
 * Builds 20-digit numbers shaped like Uzbek bank accounts: 5-digit balance account,
 * 3-digit currency code, then 12 random digits.
 */
@Component
class AccountNumberGenerator {

    private static final String BALANCE_ACCOUNT = "20206";
    private static final int MAX_ATTEMPTS = 10;

    private final SecureRandom random = new SecureRandom();
    private final AccountRepository accounts;

    AccountNumberGenerator(AccountRepository accounts) {
        this.accounts = accounts;
    }

    String next(Currency currency) {
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            String number = BALANCE_ACCOUNT + currency.accountCode() + randomDigits(12);
            if (!accounts.existsByNumber(number)) {
                return number;
            }
        }
        // 10^12 combinations per currency: reaching this means something is badly wrong.
        throw new IllegalStateException("Could not generate a unique account number");
    }

    private String randomDigits(int length) {
        StringBuilder digits = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            digits.append(random.nextInt(10));
        }
        return digits.toString();
    }
}
