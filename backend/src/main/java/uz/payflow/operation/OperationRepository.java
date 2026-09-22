package uz.payflow.operation;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface OperationRepository extends JpaRepository<Operation, Long>, JpaSpecificationExecutor<Operation> {

    Optional<Operation> findByInitiatedByAndIdempotencyKey(Long initiatedBy, String idempotencyKey);

    /**
     * History page with both accounts and their owners fetched in the same query. Without the entity
     * graph, rendering 20 rows would fire up to 80 extra SELECTs (the classic N+1 problem).
     */
    @Override
    @EntityGraph(attributePaths = {"fromAccount.owner", "toAccount.owner"})
    Page<Operation> findAll(Specification<Operation> spec, Pageable pageable);

    @Override
    @EntityGraph(attributePaths = {"fromAccount", "toAccount"})
    List<Operation> findAll(Specification<Operation> spec);
}
