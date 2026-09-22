package uz.payflow.operation;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.payflow.account.Account;
import uz.payflow.account.AccountRepository;
import uz.payflow.common.BusinessRuleException;
import uz.payflow.common.ConflictException;
import uz.payflow.common.NotFoundException;

/** Everything that changes a balance goes through here. */
@Service
public class PaymentService {

    private final AccountRepository accounts;
    private final OperationRepository operations;
    private final Clock clock;

    PaymentService(AccountRepository accounts, OperationRepository operations, Clock clock) {
        this.accounts = accounts;
        this.operations = operations;
        this.clock = clock;
    }

    @Transactional
    public OperationResponse deposit(Long userId, Long accountId, DepositRequest request) {
        Account account = accounts.lockByIdsInOrder(List.of(accountId)).stream()
                .filter(a -> a.isOwnedBy(userId))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("ACCOUNT_NOT_FOUND", "Счёт не найден"));

        BigDecimal amount = money(request.amount());
        if (amount.compareTo(account.getCurrency().maxDeposit()) > 0) {
            throw new BusinessRuleException("DEPOSIT_LIMIT", "За один раз можно пополнить не больше "
                    + account.getCurrency().maxDeposit().toPlainString() + " " + account.getCurrency());
        }

        account.credit(amount);
        Operation operation = operations.save(Operation.deposit(account, amount,
                clean(request.description()), userId, clock.instant()));
        return OperationMapper.toResponse(operation, Set.of(account.getId()));
    }

    /**
     * Moves money between two accounts of the same currency.
     *
     * <p>Retries are safe: a repeated call with the same {@code idempotencyKey} returns the original
     * operation instead of charging the sender twice. Reusing a key for a different transfer is an error.
     */
    @Transactional
    public TransferResult transfer(Long userId, String idempotencyKey, TransferRequest request) {
        BigDecimal amount = money(request.amount());

        if (idempotencyKey != null) {
            Optional<Operation> previous = operations.findByInitiatedByAndIdempotencyKey(userId, idempotencyKey);
            if (previous.isPresent()) {
                return replay(previous.get(), request, amount);
            }
        }

        // Resolve ids only: the accounts must first enter the persistence context through the locking
        // query, otherwise Hibernate would return a cached copy with a balance read before the lock.
        Long fromId = accounts.findOwnedId(request.fromAccountId(), userId)
                .orElseThrow(() -> new NotFoundException("ACCOUNT_NOT_FOUND", "Счёт списания не найден"));
        Long toId = accounts.findIdByNumber(request.toAccountNumber())
                .orElseThrow(() -> new NotFoundException("RECIPIENT_NOT_FOUND", "Счёт получателя не найден"));
        if (fromId.equals(toId)) {
            throw new BusinessRuleException("SAME_ACCOUNT", "Нельзя перевести деньги на тот же счёт");
        }

        List<Account> locked = accounts.lockByIdsInOrder(List.of(fromId, toId));
        Account from = pick(locked, fromId);
        Account to = pick(locked, toId);

        if (from.getCurrency() != to.getCurrency()) {
            throw new BusinessRuleException("CURRENCY_MISMATCH",
                    "Валюта счетов не совпадает: " + from.getCurrency() + " → " + to.getCurrency());
        }

        from.debit(amount);
        to.credit(amount);

        Operation operation = operations.save(Operation.transfer(from, to, amount,
                clean(request.description()), userId, idempotencyKey, clock.instant()));
        return new TransferResult(OperationMapper.toResponse(operation, ownAccountIds(userId, from, to)), false);
    }

    private TransferResult replay(Operation previous, TransferRequest request, BigDecimal amount) {
        boolean sameRequest = previous.getType() == OperationType.TRANSFER
                && Objects.equals(previous.getFromAccount().getId(), request.fromAccountId())
                && previous.getToAccount().getNumber().equals(request.toAccountNumber())
                && previous.getAmount().compareTo(amount) == 0;
        if (!sameRequest) {
            throw new ConflictException("IDEMPOTENCY_KEY_REUSED",
                    "Этот Idempotency-Key уже использован для другого перевода");
        }
        Long userId = previous.getInitiatedBy();
        return new TransferResult(OperationMapper.toResponse(previous,
                ownAccountIds(userId, previous.getFromAccount(), previous.getToAccount())), true);
    }

    private static Set<Long> ownAccountIds(Long userId, Account from, Account to) {
        return to.isOwnedBy(userId) ? Set.of(from.getId(), to.getId()) : Set.of(from.getId());
    }

    private static Account pick(List<Account> locked, Long id) {
        return locked.stream()
                .filter(a -> a.getId().equals(id))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("ACCOUNT_NOT_FOUND", "Счёт не найден"));
    }

    private static BigDecimal money(BigDecimal amount) {
        // Validation already guarantees at most two decimals, so this never rounds.
        return amount.setScale(2);
    }

    private static String clean(String description) {
        return description == null || description.isBlank() ? null : description.trim();
    }

    public record TransferResult(OperationResponse operation, boolean replayed) {
    }
}
