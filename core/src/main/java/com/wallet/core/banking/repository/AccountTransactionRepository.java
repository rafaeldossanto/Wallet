package com.wallet.core.banking.repository;

import com.wallet.core.banking.entity.AccountTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AccountTransactionRepository extends JpaRepository<AccountTransaction, UUID> {

    /** Includes soft-deleted rows: a transaction that comes back is revived, not duplicated. */
    List<AccountTransaction> findByAccountIdAndBookedOnBetween(UUID accountId, LocalDate from, LocalDate to);

    Optional<AccountTransaction> findByAccountIdAndProviderTransactionId(UUID accountId, String providerTransactionId);

    @Query("select max(t.bookedOn) from AccountTransaction t where t.accountId = :accountId and t.deletedAt is null")
    Optional<LocalDate> findLatestBookedOn(@Param("accountId") UUID accountId);
}
