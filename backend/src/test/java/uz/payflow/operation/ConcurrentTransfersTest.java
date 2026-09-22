package uz.payflow.operation;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import uz.payflow.account.Account;
import uz.payflow.account.AccountRepository;
import uz.payflow.account.AccountService;
import uz.payflow.account.Currency;
import uz.payflow.common.BusinessRuleException;
import uz.payflow.user.User;
import uz.payflow.user.UserRepository;

/**
 * Real database, real transactions, many threads at once. Without row locks these tests lose updates:
 * two transfers read the same balance, both pass the check, and money is created or overdrawn.
 */
@SpringBootTest
@ActiveProfiles("test")
class ConcurrentTransfersTest {

    private static final int THREADS = 20;

    @Autowired
    private PaymentService paymentService;
    @Autowired
    private AccountService accountService;
    @Autowired
    private AccountRepository accountRepository;
    @Autowired
    private UserRepository userRepository;

    @Test
    void parallelTransfersNeverOverdrawTheAccount() throws Exception {
        User alice = user("Alice Parallel");
        User bob = user("Bob Parallel");
        Account source = accountService.open(alice, Currency.UZS, "Source");
        Account target = accountService.open(bob, Currency.UZS, "Target");
        paymentService.deposit(alice.getId(), source.getId(), new DepositRequest(new BigDecimal("100.00"), null));

        AtomicInteger succeeded = new AtomicInteger();
        AtomicInteger refused = new AtomicInteger();
        runConcurrently(THREADS, i -> {
            try {
                paymentService.transfer(alice.getId(), null,
                        new TransferRequest(source.getId(), target.getNumber(), new BigDecimal("10.00"), null));
                succeeded.incrementAndGet();
            } catch (BusinessRuleException e) {
                assertThat(e.getCode()).isEqualTo("INSUFFICIENT_FUNDS");
                refused.incrementAndGet();
            }
        });

        // 100 / 10 = exactly ten transfers fit; the other ten must be refused.
        assertThat(succeeded).hasValue(10);
        assertThat(refused).hasValue(10);
        assertThat(balanceOf(source)).isEqualByComparingTo("0.00");
        assertThat(balanceOf(target)).isEqualByComparingTo("100.00");
    }

    @Test
    void oppositeTransfersDoNotDeadlock() throws Exception {
        User alice = user("Alice Opposite");
        User bob = user("Bob Opposite");
        Account a = accountService.open(alice, Currency.UZS, "A");
        Account b = accountService.open(bob, Currency.UZS, "B");
        paymentService.deposit(alice.getId(), a.getId(), new DepositRequest(new BigDecimal("1000.00"), null));
        paymentService.deposit(bob.getId(), b.getId(), new DepositRequest(new BigDecimal("1000.00"), null));

        // Half the threads send A→B, the other half B→A. Locks are taken in id order, so nobody waits
        // on a lock held by someone who is waiting on them.
        runConcurrently(THREADS, i -> {
            if (i % 2 == 0) {
                paymentService.transfer(alice.getId(), null,
                        new TransferRequest(a.getId(), b.getNumber(), new BigDecimal("7.00"), null));
            } else {
                paymentService.transfer(bob.getId(), null,
                        new TransferRequest(b.getId(), a.getNumber(), new BigDecimal("5.00"), null));
            }
        });

        // 10 × 7 moved one way, 10 × 5 the other way; nothing lost in total.
        assertThat(balanceOf(a)).isEqualByComparingTo("980.00");
        assertThat(balanceOf(b)).isEqualByComparingTo("1020.00");
    }

    private void runConcurrently(int threads, Task task) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < threads; i++) {
                int index = i;
                futures.add(pool.submit(() -> {
                    start.await();
                    task.run(index);
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> future : futures) {
                future.get(30, TimeUnit.SECONDS); // rethrows anything unexpected from a worker
            }
        } finally {
            pool.shutdownNow();
        }
    }

    private BigDecimal balanceOf(Account account) {
        return accountRepository.findById(account.getId()).orElseThrow().getBalance();
    }

    private User user(String name) {
        return userRepository.save(new User(UUID.randomUUID() + "@test.uz", "hash", name, Instant.now()));
    }

    @FunctionalInterface
    private interface Task {
        void run(int index) throws Exception;
    }
}
