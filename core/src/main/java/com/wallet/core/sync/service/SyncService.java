package com.wallet.core.sync.service;

import com.wallet.core.banking.AccountSyncData;
import com.wallet.core.banking.BankingSync;
import com.wallet.core.banking.BankingSyncResult;
import com.wallet.core.connection.ConnectionRegistry;
import com.wallet.core.connection.LinkedConnection;
import com.wallet.core.provider.FinancialDataProvider;
import com.wallet.core.provider.ProviderAccount;
import com.wallet.core.provider.ProviderInvestment;
import com.wallet.core.provider.ProviderItem;
import com.wallet.core.provider.ProviderItemStatus;
import com.wallet.core.shared.error.DomainException;
import com.wallet.core.shared.time.WalletTime;
import com.wallet.core.sync.SyncRunStatus;
import com.wallet.core.sync.SyncTrigger;
import com.wallet.core.sync.config.SyncProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

/**
 * Reads a connection's data from the provider and hands it to the modules that own it.
 * Steps are described in the vault note "Sincronização".
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SyncService {

    private final ConnectionRegistry registry;
    private final FinancialDataProvider provider;
    private final BankingSync bankingSync;
    private final SyncWriter writer;
    private final SyncRunRecorder recorder;
    private final SyncProperties properties;
    private final Clock clock;

    /** Empty when a sync of this connection is already running. */
    public Optional<UUID> start(UUID connectionId, SyncTrigger trigger) {
        return recorder.tryStart(connectionId, trigger);
    }

    /** Starts and runs in the calling thread (scheduler, first sync after linking). */
    public SyncRunStatus syncNow(UUID connectionId, SyncTrigger trigger) {
        Optional<UUID> runId = start(connectionId, trigger);
        if (runId.isEmpty()) {
            log.info("[SYNC] Connection {} already syncing; {} run skipped", connectionId, trigger);
            return SyncRunStatus.SKIPPED;
        }
        return run(runId.get(), connectionId, trigger);
    }

    /** Never throws: every ending, failures included, is recorded on the run. */
    public SyncRunStatus run(UUID runId, UUID connectionId, SyncTrigger trigger) {
        SyncOutcome outcome;
        try {
            outcome = execute(connectionId, trigger);
        } catch (DomainException ex) {
            log.warn("[SYNC] Connection {} ({}) failed: {}", connectionId, trigger, ex.getCode());
            outcome = SyncOutcome.failed(ex.getCode());
        } catch (RuntimeException ex) {
            log.error("[SYNC] Connection {} ({}) failed unexpectedly", connectionId, trigger, ex);
            outcome = SyncOutcome.failed("sync.unexpected_error");
        }
        if (!SyncRunStatus.SUCCEEDED.equals(outcome.status())) {
            registry.recordSyncEnded(connectionId);
        }
        recorder.finish(runId, outcome);
        return outcome.status();
    }

    private SyncOutcome execute(UUID connectionId, SyncTrigger trigger) {
        Optional<LinkedConnection> found = registry.find(connectionId);
        if (found.isEmpty() || !registry.markSyncing(connectionId)) {
            return connectionGone(connectionId, trigger);
        }
        LinkedConnection connection = found.get();

        ProviderItem item = provider.findItem(connection.providerItemId());
        if (ProviderItemStatus.NEEDS_USER_ACTION.equals(item.status())) {
            registry.recordNeedsAttention(connectionId);
            return SyncOutcome.failed("connection.needs_attention");
        }
        if (ProviderItemStatus.UPDATING.equals(item.status())) {
            return SyncOutcome.skipped("provider.updating");
        }
        if (SyncTrigger.SCHEDULED.equals(trigger) && nothingNew(connection, item)) {
            return SyncOutcome.skipped("sync.nothing_new");
        }

        LocalDate today = WalletTime.today(clock);
        List<AccountSyncData> accounts = provider.listAccounts(connection.providerItemId()).stream()
                .map(account -> readAccount(connection, account, today))
                .toList();
        List<ProviderInvestment> investments = provider.listInvestments(connection.providerItemId());

        Optional<BankingSyncResult> written = writer.write(connection, item, today, accounts, investments);
        if (written.isEmpty()) {
            return connectionGone(connectionId, trigger);
        }
        BankingSyncResult result = written.get();
        log.info("[SYNC] Connection {} ({}): {} accounts, {} transactions upserted, {} removed",
                connectionId, trigger, result.accounts(), result.transactionsUpserted(), result.transactionsDeleted());
        return SyncOutcome.succeeded(result.accounts(), result.transactionsUpserted(), result.transactionsDeleted());
    }

    /** Unlinking mid-sync is a normal user action, not a failure. */
    private static SyncOutcome connectionGone(UUID connectionId, SyncTrigger trigger) {
        log.info("[SYNC] Connection {} ({}) was unlinked; sync stopped", connectionId, trigger);
        return SyncOutcome.skipped("connection.gone");
    }

    private static boolean nothingNew(LinkedConnection connection, ProviderItem item) {
        return nonNull(connection.providerUpdatedAt())
                && (isNull(item.lastUpdatedAt()) || !item.lastUpdatedAt().isAfter(connection.providerUpdatedAt()));
    }

    private AccountSyncData readAccount(LinkedConnection connection, ProviderAccount account, LocalDate today) {
        LocalDate oldest = today.minusDays(properties.firstWindow().toDays());
        LocalDate from = bankingSync.latestBookedOn(connection.id(), account.id())
                .map(latest -> latest.minusDays(properties.overlap().toDays()))
                .filter(start -> start.isAfter(oldest))
                .orElse(oldest);
        return new AccountSyncData(
                account,
                from,
                today,
                provider.listTransactions(account.id(), account.kind(), from, today),
                account.kind().isCreditCard() ? provider.listBills(account.id()) : List.of());
    }
}
