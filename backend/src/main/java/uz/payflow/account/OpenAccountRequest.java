package uz.payflow.account;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record OpenAccountRequest(
        @NotNull(message = "Выберите валюту") Currency currency,
        @NotBlank(message = "Укажите название счёта") @Size(max = 60, message = "Название: не длиннее 60 символов")
        String name) {
}
