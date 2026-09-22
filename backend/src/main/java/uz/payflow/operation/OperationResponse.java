package uz.payflow.operation;

import java.math.BigDecimal;
import java.time.Instant;

import uz.payflow.account.Currency;

/**
 * An operation as seen by one user.
 *
 * @param accountNumber      the viewer's account involved
 * @param counterpartyNumber the other side; {@code null} for deposits
 * @param counterpartyName   masked owner name of the other side
 */
public record OperationResponse(Long id, OperationType type, Direction direction, BigDecimal amount,
                                Currency currency, String accountNumber, String counterpartyNumber,
                                String counterpartyName, String description, Instant createdAt) {
}
