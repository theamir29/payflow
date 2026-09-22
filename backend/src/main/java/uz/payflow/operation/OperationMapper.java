package uz.payflow.operation;

import java.util.Set;

import uz.payflow.account.Account;

final class OperationMapper {

    private OperationMapper() {
    }

    /** @param viewerAccountIds all accounts of the user looking at the operation */
    static OperationResponse toResponse(Operation op, Set<Long> viewerAccountIds) {
        Direction direction = Direction.of(op, viewerAccountIds);
        Account mine = direction == Direction.IN ? op.getToAccount() : op.getFromAccount();
        Account other = direction == Direction.IN ? op.getFromAccount() : op.getToAccount();

        return new OperationResponse(op.getId(), op.getType(), direction, op.getAmount(), op.getCurrency(),
                mine.getNumber(),
                other == null ? null : other.getNumber(),
                other == null ? null : other.getOwner().maskedName(),
                op.getDescription(), op.getCreatedAt());
    }
}
