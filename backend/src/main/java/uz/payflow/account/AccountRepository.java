package uz.payflow.account;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

public interface AccountRepository extends JpaRepository<Account, Long> {

    List<Account> findByOwnerIdOrderByIdAsc(Long ownerId);

    List<Account> findByOwnerIdAndCurrency(Long ownerId, Currency currency);

    long countByOwnerId(Long ownerId);

    Optional<Account> findByIdAndOwnerId(Long id, Long ownerId);

    Optional<Account> findByNumber(String number);

    boolean existsByNumber(String number);

    // Id-only lookups used right before locking. Loading whole entities here would put them into the
    // persistence context, and the later FOR UPDATE query would hand back those cached, possibly stale
    // copies instead of the freshly locked rows.

    @Query("select a.id from Account a where a.id = :id and a.owner.id = :ownerId")
    Optional<Long> findOwnedId(@Param("id") Long id, @Param("ownerId") Long ownerId);

    @Query("select a.id from Account a where a.number = :number")
    Optional<Long> findIdByNumber(@Param("number") String number);

    /**
     * {@code SELECT ... FOR UPDATE} on every account a money movement touches. Rows are always locked in
     * ascending id order: two opposite transfers A→B and B→A then queue up instead of deadlocking.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000"))
    @Query("select a from Account a where a.id in :ids order by a.id")
    List<Account> lockByIdsInOrder(@Param("ids") Collection<Long> ids);
}
