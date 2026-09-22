package uz.payflow.operation;

import java.util.Set;

/** How an operation looks from the viewer's side. */
public enum Direction {
    /** Money came into one of the viewer's accounts. */
    IN,
    /** Money left one of the viewer's accounts. */
    OUT,
    /** Transfer between two of the viewer's own accounts. */
    INTERNAL;

    public static Direction of(Operation operation, Set<Long> viewerAccountIds) {
        boolean fromMine = operation.getFromAccount() != null
                && viewerAccountIds.contains(operation.getFromAccount().getId());
        boolean toMine = viewerAccountIds.contains(operation.getToAccount().getId());
        if (fromMine && toMine) {
            return INTERNAL;
        }
        return toMine ? IN : OUT;
    }
}
