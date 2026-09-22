package uz.payflow.stats;

import java.math.BigDecimal;

/** @param month calendar month in {@code yyyy-MM} form */
public record MonthlyStat(String month, BigDecimal income, BigDecimal expense) {
}
