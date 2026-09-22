package uz.payflow.operation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import uz.payflow.account.Account;
import uz.payflow.account.AccountRepository;
import uz.payflow.account.Currency;
import uz.payflow.common.BusinessRuleException;
import uz.payflow.common.ConflictException;
import uz.payflow.user.User;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    private static final Long SENDER = 10L;
    private static final Instant NOW = Instant.parse("2026-09-01T10:00:00Z");

    @Mock
    private AccountRepository accounts;
    @Mock
    private OperationRepository operations;

    private PaymentService service;
    private Account from;
    private Account to;

    @BeforeEach
    void setUp() {
        service = new PaymentService(accounts, operations, Clock.fixed(NOW, ZoneOffset.UTC));
        from = account(1L, SENDER, "Sender Person", Currency.UZS, "1000.00");
        to = account(2L, 20L, "Receiver Person", Currency.UZS, "0.00");
    }

    @Test
    void movesMoneyAndRecordsTheOperation() {
        givenAccountsResolveAndLock(from, to);
        when(operations.save(any(Operation.class))).thenAnswer(call -> call.getArgument(0));

        PaymentService.TransferResult result = service.transfer(SENDER, "key-00000001", request(to, "250.00"));

        assertThat(from.getBalance()).isEqualByComparingTo("750.00");
        assertThat(to.getBalance()).isEqualByComparingTo("250.00");
        assertThat(result.replayed()).isFalse();
        assertThat(result.operation().direction()).isEqualTo(Direction.OUT);
        assertThat(result.operation().counterpartyName()).isEqualTo("Receiver P.");
    }

    @Test
    void refusesWhenFundsAreInsufficientAndChangesNothing() {
        givenAccountsResolveAndLock(from, to);

        assertThatThrownBy(() -> service.transfer(SENDER, null, request(to, "1000.01")))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("code").isEqualTo("INSUFFICIENT_FUNDS");

        assertThat(from.getBalance()).isEqualByComparingTo("1000.00");
        assertThat(to.getBalance()).isEqualByComparingTo("0.00");
        verify(operations, never()).save(any());
    }

    @Test
    void refusesTransfersBetweenDifferentCurrencies() {
        Account dollars = account(3L, 20L, "Receiver Person", Currency.USD, "0.00");
        givenAccountsResolveAndLock(from, dollars);

        assertThatThrownBy(() -> service.transfer(SENDER, null, request(dollars, "10.00")))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("code").isEqualTo("CURRENCY_MISMATCH");
        assertThat(from.getBalance()).isEqualByComparingTo("1000.00");
    }

    @Test
    void refusesTransferToTheSameAccountBeforeTakingLocks() {
        when(accounts.findOwnedId(from.getId(), SENDER)).thenReturn(Optional.of(from.getId()));
        when(accounts.findIdByNumber(from.getNumber())).thenReturn(Optional.of(from.getId()));

        assertThatThrownBy(() -> service.transfer(SENDER, null, request(from, "10.00")))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("code").isEqualTo("SAME_ACCOUNT");
        verify(accounts, never()).lockByIdsInOrder(anyCollection());
    }

    @Test
    void retryWithTheSameIdempotencyKeyReturnsTheOriginalOperation() {
        Operation original = Operation.transfer(from, to, new BigDecimal("250.00"), null, SENDER, "key-00000001", NOW);
        when(operations.findByInitiatedByAndIdempotencyKey(SENDER, "key-00000001")).thenReturn(Optional.of(original));

        PaymentService.TransferResult result = service.transfer(SENDER, "key-00000001", request(to, "250.00"));

        assertThat(result.replayed()).isTrue();
        assertThat(from.getBalance()).isEqualByComparingTo("1000.00");
        verify(accounts, never()).lockByIdsInOrder(anyCollection());
        verify(operations, never()).save(any());
    }

    @Test
    void reusingAnIdempotencyKeyForAnotherAmountIsAConflict() {
        Operation original = Operation.transfer(from, to, new BigDecimal("250.00"), null, SENDER, "key-00000001", NOW);
        when(operations.findByInitiatedByAndIdempotencyKey(SENDER, "key-00000001")).thenReturn(Optional.of(original));

        assertThatThrownBy(() -> service.transfer(SENDER, "key-00000001", request(to, "999.00")))
                .isInstanceOf(ConflictException.class)
                .extracting("code").isEqualTo("IDEMPOTENCY_KEY_REUSED");
    }

    private void givenAccountsResolveAndLock(Account source, Account target) {
        when(accounts.findOwnedId(source.getId(), SENDER)).thenReturn(Optional.of(source.getId()));
        when(accounts.findIdByNumber(target.getNumber())).thenReturn(Optional.of(target.getId()));
        when(accounts.lockByIdsInOrder(List.of(source.getId(), target.getId()))).thenReturn(List.of(source, target));
    }

    private TransferRequest request(Account target, String amount) {
        return new TransferRequest(from.getId(), target.getNumber(), new BigDecimal(amount), null);
    }

    private static Account account(Long id, Long ownerId, String ownerName, Currency currency, String balance) {
        User owner = new User(ownerId + "@test.uz", "hash", ownerName, NOW);
        ReflectionTestUtils.setField(owner, "id", ownerId);
        Account account = new Account(String.format("20206%s%012d", currency.accountCode(), id),
                owner, currency, "Test", NOW);
        ReflectionTestUtils.setField(account, "id", id);
        BigDecimal initial = new BigDecimal(balance);
        if (initial.signum() > 0) {
            account.credit(initial);
        }
        return account;
    }
}
