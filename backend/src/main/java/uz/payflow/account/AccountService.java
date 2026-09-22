package uz.payflow.account;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.payflow.common.BusinessRuleException;
import uz.payflow.common.NotFoundException;
import uz.payflow.user.User;
import uz.payflow.user.UserRepository;

@Service
public class AccountService {

    static final int MAX_ACCOUNTS_PER_USER = 5;

    private final AccountRepository accounts;
    private final UserRepository users;
    private final AccountNumberGenerator numberGenerator;
    private final Clock clock;

    AccountService(AccountRepository accounts, UserRepository users,
                   AccountNumberGenerator numberGenerator, Clock clock) {
        this.accounts = accounts;
        this.users = users;
        this.numberGenerator = numberGenerator;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<AccountResponse> list(Long userId) {
        return accounts.findByOwnerIdOrderByIdAsc(userId).stream()
                .map(AccountResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public AccountResponse get(Long userId, Long accountId) {
        return AccountResponse.from(findOwned(userId, accountId));
    }

    @Transactional
    public AccountResponse open(Long userId, OpenAccountRequest request) {
        if (accounts.countByOwnerId(userId) >= MAX_ACCOUNTS_PER_USER) {
            throw new BusinessRuleException("ACCOUNT_LIMIT", "Можно открыть не больше " + MAX_ACCOUNTS_PER_USER + " счетов");
        }
        User owner = users.getReferenceById(userId);
        return AccountResponse.from(open(owner, request.currency(), request.name().trim()));
    }

    @Transactional
    public Account open(User owner, Currency currency, String name) {
        return open(owner, currency, name, clock.instant());
    }

    @Transactional
    public Account open(User owner, Currency currency, String name, Instant createdAt) {
        Account account = new Account(numberGenerator.next(currency), owner, currency, name, createdAt);
        return accounts.save(account);
    }

    @Transactional(readOnly = true)
    public RecipientResponse lookup(String number) {
        Account account = accounts.findByNumber(number)
                .orElseThrow(() -> new NotFoundException("RECIPIENT_NOT_FOUND", "Счёт получателя не найден"));
        return new RecipientResponse(account.getNumber(), account.getOwner().maskedName(), account.getCurrency());
    }

    Account findOwned(Long userId, Long accountId) {
        // Someone else's account answers exactly like a missing one: no way to probe other users' ids.
        return accounts.findByIdAndOwnerId(accountId, userId)
                .orElseThrow(() -> new NotFoundException("ACCOUNT_NOT_FOUND", "Счёт не найден"));
    }
}
