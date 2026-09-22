package uz.payflow.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank(message = "Укажите email") @Email(message = "Некорректный email") @Size(max = 255)
        String email,

        // BCrypt only looks at the first 72 bytes, so longer passwords would be silently truncated.
        @NotBlank(message = "Укажите пароль") @Size(min = 8, max = 72, message = "Пароль: от 8 до 72 символов")
        String password,

        @NotBlank(message = "Укажите имя") @Size(max = 100, message = "Имя: не длиннее 100 символов")
        String fullName) {
}
