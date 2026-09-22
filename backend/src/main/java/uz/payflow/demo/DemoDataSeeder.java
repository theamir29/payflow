package uz.payflow.demo;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import uz.payflow.account.Account;
import uz.payflow.account.AccountService;
import uz.payflow.account.Currency;
import uz.payflow.config.PayflowProperties;
import uz.payflow.operation.Operation;
import uz.payflow.operation.OperationRepository;
import uz.payflow.user.User;
import uz.payflow.user.UserRepository;

/**
 * Fills an empty database with a demo user and five months of plausible history, so that the public demo
 * has something to show. Runs once: if the demo user already exists, nothing happens.
 */
@Component
@ConditionalOnProperty(name = "payflow.demo.enabled", havingValue = "true")
class DemoDataSeeder implements ApplicationRunner {

    static final String DEMO_EMAIL = "demo@payflow.uz";
    static final String DEMO_PASSWORD = "demo12345";

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    private final UserRepository users;
    private final AccountService accountService;
    private final OperationRepository operations;
    private final PasswordEncoder passwordEncoder;
    private final PayflowProperties properties;
    private final Clock clock;

    DemoDataSeeder(UserRepository users, AccountService accountService, OperationRepository operations,
                   PasswordEncoder passwordEncoder, PayflowProperties properties, Clock clock) {
        this.users = users;
        this.accountService = accountService;
        this.operations = operations;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (users.existsByEmailIgnoreCase(DEMO_EMAIL)) {
            return;
        }
        Instant now = clock.instant();
        Instant opened = now.minus(160, ChronoUnit.DAYS);

        User demo = user(DEMO_EMAIL, DEMO_PASSWORD, "Демо Пользователь", opened);
        User aziz = user("aziz@payflow.uz", UUID.randomUUID().toString(), "Азиз Каримов", opened);
        User malika = user("malika@payflow.uz", UUID.randomUUID().toString(), "Малика Юсупова", opened);

        Account main = accountService.open(demo, Currency.UZS, "Основной счёт", opened);
        Account savings = accountService.open(demo, Currency.UZS, "Накопления", opened);
        Account dollars = accountService.open(demo, Currency.USD, "Долларовый счёт", opened);
        Account azizUzs = accountService.open(aziz, Currency.UZS, "Основной счёт", opened);
        Account azizUsd = accountService.open(aziz, Currency.USD, "Долларовый счёт", opened);
        Account malikaUzs = accountService.open(malika, Currency.UZS, "Основной счёт", opened);

        String[] salaries = {"9500000", "9500000", "10200000", "10200000", "10200000"};
        String[] rent = {"1200000", "1200000", "1250000", "1250000", "1300000"};
        String[] lunch = {"180000", "240000", "95000", "310000", "150000"};
        String[] savingsTopUps = {"1500000", "2000000", "1500000", "2500000", "2000000"};

        for (int monthsAgo = 4; monthsAgo >= 0; monthsAgo--) {
            int i = 4 - monthsAgo;
            deposit(malikaUzs, "3000000", "Пополнение", at(monthsAgo, 1, now), malika);
            deposit(main, salaries[i], "Зарплата", at(monthsAgo, 5, now), demo);
            transfer(main, azizUzs, rent[i], "Аренда, моя доля", at(monthsAgo, 7, now), demo);
            transfer(malikaUzs, main, lunch[i], "Возврат за обед", at(monthsAgo, 12, now), malika);
            transfer(main, savings, savingsTopUps[i], "В копилку", at(monthsAgo, 15, now), demo);
            transfer(main, malikaUzs, "400000", "На подарок Нодире", at(monthsAgo, 21, now), demo);
        }
        deposit(dollars, "500.00", "Перевод из-за рубежа", at(3, 10, now), demo);
        transfer(dollars, azizUsd, "120.00", "Онлайн-курс, половина", at(1, 18, now), demo);

        log.info("Demo data created: {} / {}", DEMO_EMAIL, DEMO_PASSWORD);
    }

    private User user(String email, String password, String name, Instant createdAt) {
        return users.save(new User(email, passwordEncoder.encode(password), name, createdAt));
    }

    private void deposit(Account to, String amount, String description, Instant at, User by) {
        if (at == null) {
            return;
        }
        BigDecimal value = new BigDecimal(amount).setScale(2);
        to.credit(value);
        operations.save(Operation.deposit(to, value, description, by.getId(), at));
    }

    private void transfer(Account from, Account to, String amount, String description, Instant at, User by) {
        if (at == null) {
            return;
        }
        BigDecimal value = new BigDecimal(amount).setScale(2);
        from.debit(value);
        to.credit(value);
        operations.save(Operation.transfer(from, to, value, description, by.getId(), null, at));
    }

    /** A moment {@code monthsAgo} months back on the given day, or {@code null} if that is still in the future. */
    private Instant at(int monthsAgo, int day, Instant now) {
        ZoneId zone = properties.zone();
        YearMonth month = YearMonth.now(clock.withZone(zone)).minusMonths(monthsAgo);
        Instant moment = month.atDay(Math.min(day, month.lengthOfMonth()))
                .atTime(LocalTime.of(10, 30).plusMinutes(day * 7L))
                .atZone(zone)
                .toInstant();
        return moment.isBefore(now) ? moment : null;
    }
}
