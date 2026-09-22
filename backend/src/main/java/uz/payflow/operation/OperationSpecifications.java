package uz.payflow.operation;

import java.time.Instant;
import java.util.Collection;

import org.springframework.data.jpa.domain.Specification;

/** Building blocks for the history filters; a {@code null} argument means "no filter". */
public final class OperationSpecifications {

    private OperationSpecifications() {
    }

    public static Specification<Operation> touchesAnyOf(Collection<Long> accountIds) {
        return (root, query, cb) -> cb.or(
                root.get("fromAccount").get("id").in(accountIds),
                root.get("toAccount").get("id").in(accountIds));
    }

    public static Specification<Operation> hasType(OperationType type) {
        return type == null ? null : (root, query, cb) -> cb.equal(root.get("type"), type);
    }

    public static Specification<Operation> createdFrom(Instant from) {
        return from == null ? null : (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("createdAt"), from);
    }

    public static Specification<Operation> createdBefore(Instant to) {
        return to == null ? null : (root, query, cb) -> cb.lessThan(root.get("createdAt"), to);
    }
}
