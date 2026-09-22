package uz.payflow.account;

import java.math.BigDecimal;

public enum Currency {

    UZS("000", new BigDecimal("50000000.00")),
    USD("840", new BigDecimal("5000.00"));

    /** Three digits of the currency inside a 20-digit Uzbek account number (UZS is "000"). */
    private final String accountCode;

    /** Largest single top-up allowed. This is a demo wallet, so money is created out of thin air. */
    private final BigDecimal maxDeposit;

    Currency(String accountCode, BigDecimal maxDeposit) {
        this.accountCode = accountCode;
        this.maxDeposit = maxDeposit;
    }

    public String accountCode() {
        return accountCode;
    }

    public BigDecimal maxDeposit() {
        return maxDeposit;
    }
}
