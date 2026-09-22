package uz.payflow.operation;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record DepositRequest(
        @NotNull(message = "Укажите сумму")
        @DecimalMin(value = "0.01", message = "Сумма должна быть больше нуля")
        @Digits(integer = 15, fraction = 2, message = "Не больше двух знаков после запятой")
        BigDecimal amount,

        @Size(max = 140, message = "Комментарий: не длиннее 140 символов")
        String description) {
}
