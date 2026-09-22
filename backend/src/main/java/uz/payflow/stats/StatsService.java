package uz.payflow.stats;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.reducing;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.payflow.account.Account;
import uz.payflow.account.AccountRepository;
import uz.payflow.account.Currency;
import uz.payflow.config.PayflowProperties;
import uz.payflow.operation.Direction;
import uz.payflow.operation.Operation;
import uz.payflow.operation.OperationRepository;
import uz.payflow.operation.OperationSpecifications;

@Service
public class StatsService {

    private static final BigDecimal ZERO = new BigDecimal("0.00");

    private final AccountRepository accounts;
    private final OperationRepository operations;
    private final PayflowProperties properties;
    private final Clock clock;

    StatsService(AccountRepository accounts, OperationRepository operations,
                 PayflowProperties properties, Clock clock) {
        this.accounts = accounts;
        this.operations = operations;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * Income and expense per calendar month for the last {@code months} months, oldest first. Months
     * without operations are present with zeros, so a chart can be drawn straight from the result.
     * Transfers between the user's own accounts are neither income nor expense and are skipped.
     */
    @Transactional(readOnly = true)
    public List<MonthlyStat> monthly(Long userId, Currency currency, int months) {
        ZoneId zone = properties.zone();
        YearMonth first = YearMonth.now(clock.withZone(zone)).minusMonths(months - 1L);
        Instant from = first.atDay(1).atStartOfDay(zone).toInstant();

        Set<Long> ids = accounts.findByOwnerIdAndCurrency(userId, currency).stream()
                .map(Account::getId)
                .collect(Collectors.toSet());

        Map<YearMonth, Map<Direction, BigDecimal>> totals = ids.isEmpty() ? Map.of() : operations
                .findAll(Specification.allOf(
                        OperationSpecifications.touchesAnyOf(ids),
                        OperationSpecifications.createdFrom(from)))
                .stream()
                .filter(op -> Direction.of(op, ids) != Direction.INTERNAL)
                .collect(groupingBy(
                        op -> YearMonth.from(op.getCreatedAt().atZone(zone)),
                        groupingBy(op -> Direction.of(op, ids),
                                reducing(ZERO, Operation::getAmount, BigDecimal::add))));

        return Stream.iterate(first, month -> month.plusMonths(1))
                .limit(months)
                .map(month -> {
                    Map<Direction, BigDecimal> sums = totals.getOrDefault(month, Map.of());
                    return new MonthlyStat(month.toString(),
                            sums.getOrDefault(Direction.IN, ZERO),
                            sums.getOrDefault(Direction.OUT, ZERO));
                })
                .toList();
    }
}
