package uz.payflow.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import uz.payflow.common.BusinessRuleException;
import uz.payflow.user.User;

class AccountTest {

    private final Account account = new Account("20206000000000000001",
            new User("a@test.uz", "hash", "Alice Tester", Instant.EPOCH), Currency.UZS, "Main", Instant.EPOCH);

    @Test
    void creditAndDebitChangeTheBalance() {
        account.credit(new BigDecimal("100.00"));
        account.debit(new BigDecimal("40.50"));

        assertThat(account.getBalance()).isEqualByComparingTo("59.50");
    }

    @Test
    void cannotDebitMoreThanTheBalance() {
        account.credit(new BigDecimal("10.00"));

        assertThatThrownBy(() -> account.debit(new BigDecimal("10.01")))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("code").isEqualTo("INSUFFICIENT_FUNDS");
        assertThat(account.getBalance()).isEqualByComparingTo("10.00");
    }

    @Test
    void rejectsZeroAndNegativeAmounts() {
        assertThatThrownBy(() -> account.credit(BigDecimal.ZERO)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> account.debit(new BigDecimal("-1"))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void masksOwnerNameForRecipientLookup() {
        assertThat(new User("x@test.uz", "h", "Амир Баймуратов", Instant.EPOCH).maskedName()).isEqualTo("Амир Б.");
        assertThat(new User("x@test.uz", "h", "  Амир  ", Instant.EPOCH).maskedName()).isEqualTo("Амир");
    }
}
