package uz.payflow.user;

import java.time.Instant;

import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uz.payflow.common.NotFoundException;
import uz.payflow.config.CurrentUserId;

@Tag(name = "Profile", description = "The signed-in user")
@RestController
@RequestMapping("/api/me")
class ProfileController {

    private final UserRepository users;

    ProfileController(UserRepository users) {
        this.users = users;
    }

    @GetMapping
    ProfileResponse me(@CurrentUserId Long userId) {
        User user = users.findById(userId)
                .orElseThrow(() -> new NotFoundException("USER_NOT_FOUND", "Пользователь не найден"));
        return new ProfileResponse(user.getId(), user.getEmail(), user.getFullName(), user.getCreatedAt());
    }

    record ProfileResponse(Long id, String email, String fullName, Instant createdAt) {
    }
}
