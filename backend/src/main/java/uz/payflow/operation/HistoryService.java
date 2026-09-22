package uz.payflow.operation;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.payflow.account.Account;
import uz.payflow.account.AccountRepository;
import uz.payflow.common.NotFoundException;

@Service
public class HistoryService {

    static final int MAX_PAGE_SIZE = 100;

    private final AccountRepository accounts;
    private final OperationRepository operations;

    HistoryService(AccountRepository accounts, OperationRepository operations) {
        this.accounts = accounts;
        this.operations = operations;
    }

    /**
     * @param accountId only operations of this account; {@code null} for all of the user's accounts
     * @param from      inclusive lower bound, optional
     * @param to        exclusive upper bound, optional
     */
    @Transactional(readOnly = true)
    public PageResponse<OperationResponse> history(Long userId, Long accountId, OperationType type,
                                                   Instant from, Instant to, int page, int size) {
        Set<Long> ownIds = accounts.findByOwnerIdOrderByIdAsc(userId).stream()
                .map(Account::getId)
                .collect(Collectors.toSet());

        List<Long> scope;
        if (accountId == null) {
            scope = List.copyOf(ownIds);
        } else if (ownIds.contains(accountId)) {
            scope = List.of(accountId);
        } else {
            throw new NotFoundException("ACCOUNT_NOT_FOUND", "Счёт не найден");
        }
        if (scope.isEmpty()) {
            return PageResponse.empty(page, size);
        }

        Specification<Operation> spec = Specification.allOf(
                OperationSpecifications.touchesAnyOf(scope),
                OperationSpecifications.hasType(type),
                OperationSpecifications.createdFrom(from),
                OperationSpecifications.createdBefore(to));

        PageRequest pageRequest = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        Page<Operation> result = operations.findAll(spec, pageRequest);
        return PageResponse.of(result.map(op -> OperationMapper.toResponse(op, ownIds)));
    }
}
