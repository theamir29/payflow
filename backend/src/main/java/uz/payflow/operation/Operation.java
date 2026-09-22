package uz.payflow.operation;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import uz.payflow.account.Account;
import uz.payflow.account.Currency;

/**
 * One money movement. Rows are only ever inserted, never updated: the history is an append-only ledger
 * and account balances are the running result of it.
 */
@Entity
@Table(name = "operations")
public class Operation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 16)
    private OperationType type;

    /** Empty for deposits: that money comes from outside the system. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "from_account_id")
    private Account fromAccount;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "to_account_id")
    private Account toAccount;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 3)
    private Currency currency;

    @Column(length = 140)
    private String description;

    @Column(name = "initiated_by", nullable = false)
    private Long initiatedBy;

    @Column(name = "idempotency_key", length = 64)
    private String idempotencyKey;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Operation() {
        // for JPA
    }

    private Operation(OperationType type, Account fromAccount, Account toAccount, BigDecimal amount,
                      String description, Long initiatedBy, String idempotencyKey, Instant createdAt) {
        this.type = type;
        this.fromAccount = fromAccount;
        this.toAccount = toAccount;
        this.amount = amount;
        this.currency = toAccount.getCurrency();
        this.description = description;
        this.initiatedBy = initiatedBy;
        this.idempotencyKey = idempotencyKey;
        this.createdAt = createdAt;
    }

    public static Operation deposit(Account to, BigDecimal amount, String description,
                                    Long initiatedBy, Instant createdAt) {
        return new Operation(OperationType.DEPOSIT, null, to, amount, description, initiatedBy, null, createdAt);
    }

    public static Operation transfer(Account from, Account to, BigDecimal amount, String description,
                                     Long initiatedBy, String idempotencyKey, Instant createdAt) {
        return new Operation(OperationType.TRANSFER, from, to, amount, description, initiatedBy, idempotencyKey, createdAt);
    }

    public Long getId() {
        return id;
    }

    public OperationType getType() {
        return type;
    }

    public Account getFromAccount() {
        return fromAccount;
    }

    public Account getToAccount() {
        return toAccount;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public Currency getCurrency() {
        return currency;
    }

    public String getDescription() {
        return description;
    }

    public Long getInitiatedBy() {
        return initiatedBy;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
