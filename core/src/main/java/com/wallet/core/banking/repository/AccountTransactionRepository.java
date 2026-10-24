package com.wallet.core.banking.repository;

import com.wallet.core.banking.entity.AccountTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AccountTransactionRepository
        extends JpaRepository<AccountTransaction, UUID>, JpaSpecificationExecutor<AccountTransaction> {

    /** Includes soft-deleted rows: a transaction that comes back is revived, not duplicated. */
    List<AccountTransaction> findByAccountIdAndBookedOnBetween(UUID accountId, LocalDate from, LocalDate to);

    Optional<AccountTransaction> findByAccountIdAndProviderTransactionId(UUID accountId, String providerTransactionId);

    List<AccountTransaction> findByAccountIdAndDeletedAtIsNullAndBookedOnBetween(UUID accountId, LocalDate from, LocalDate to);

    List<AccountTransaction> findByAccountIdAndDeletedAtIsNullAndProviderBillId(UUID accountId, String providerBillId);

    List<AccountTransaction> findByUserIdAndDeletedAtIsNullOrderByBookedOnAscCreatedAtAscIdAsc(UUID userId);

    /** Transactions the provider stopped reporting before {@code before}: gone for good, nothing shows them. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from AccountTransaction t where t.deletedAt < :before")
    int deleteRemovedBefore(@Param("before") Instant before);

    @Query("select max(t.bookedOn) from AccountTransaction t where t.accountId = :accountId and t.deletedAt is null")
    Optional<LocalDate> findLatestBookedOn(@Param("accountId") UUID accountId);
}
