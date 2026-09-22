package uz.payflow.auth;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank(message = "Укажите email") String email,
        @NotBlank(message = "Укажите пароль") String password) {
}
