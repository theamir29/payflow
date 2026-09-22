package uz.payflow.auth;

import java.time.Clock;
import java.util.Locale;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.payflow.account.AccountService;
import uz.payflow.account.Currency;
import uz.payflow.common.ConflictException;
import uz.payflow.common.UnauthorizedException;
import uz.payflow.user.User;
import uz.payflow.user.UserRepository;

@Service
public class AuthService {

    private final UserRepository users;
    private final AccountService accounts;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokens;
    private final Clock clock;

    AuthService(UserRepository users, AccountService accounts, PasswordEncoder passwordEncoder,
                TokenService tokens, Clock clock) {
        this.users = users;
        this.accounts = accounts;
        this.passwordEncoder = passwordEncoder;
        this.tokens = tokens;
        this.clock = clock;
    }

    /** Creates the user together with a first UZS account, so the wallet is usable right away. */
    @Transactional
    public TokenResponse register(RegisterRequest request) {
        String email = normalizeEmail(request.email());
        if (users.existsByEmailIgnoreCase(email)) {
            throw new ConflictException("EMAIL_TAKEN", "Этот email уже зарегистрирован");
        }
        User user = users.save(new User(email, passwordEncoder.encode(request.password()),
                request.fullName().trim(), clock.instant()));
        accounts.open(user, Currency.UZS, "Основной счёт");
        return tokens.issue(user);
    }

    @Transactional(readOnly = true)
    public TokenResponse login(LoginRequest request) {
        return users.findByEmailIgnoreCase(normalizeEmail(request.email()))
                .filter(user -> passwordEncoder.matches(request.password(), user.getPasswordHash()))
                .map(tokens::issue)
                // Same answer for "no such user" and "wrong password": don't reveal which emails exist.
                .orElseThrow(() -> new UnauthorizedException("BAD_CREDENTIALS", "Неверный email или пароль"));
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
