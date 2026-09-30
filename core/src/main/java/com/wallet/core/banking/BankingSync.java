package com.wallet.core.banking;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** The banking module's API for the sync. Banking owns its tables; the sync only hands it data. */
public interface BankingSync {

    /** Latest non-deleted transaction day of an account, to size the next window. Empty = never synced. */
    Optional<LocalDate> latestBookedOn(UUID connectionId, String providerAccountId);

    /** Writes accounts, today's balances, transactions and bills. Joins the caller's transaction. */
    BankingSyncResult apply(UUID connectionId, UUID userId, LocalDate today, List<AccountSyncData> accounts);
}
