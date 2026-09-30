package com.wallet.core.sync.service;

import com.wallet.core.banking.AccountSyncData;
import com.wallet.core.banking.BankingSync;
import com.wallet.core.banking.BankingSyncResult;
import com.wallet.core.connection.ConnectionRegistry;
import com.wallet.core.connection.LinkedConnection;
import com.wallet.core.investment.InvestmentSync;
import com.wallet.core.provider.ProviderInvestment;
import com.wallet.core.provider.ProviderItem;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

/**
 * The only transactional step of a sync. Everything was already read from the provider, so the
 * database is never held open while waiting on the network, and the write is all or nothing.
 */
@Service
@RequiredArgsConstructor
class SyncWriter {

    private final BankingSync bankingSync;
    private final InvestmentSync investmentSync;
    private final ConnectionRegistry registry;
    private final Clock clock;

    @Transactional
    public BankingSyncResult write(LinkedConnection connection, ProviderItem item, LocalDate today,
                                   List<AccountSyncData> accounts, List<ProviderInvestment> investments) {
        BankingSyncResult result = bankingSync.apply(connection.id(), connection.userId(), today, accounts);
        investmentSync.apply(connection.id(), connection.userId(), today, investments);
        registry.recordSyncSucceeded(connection.id(), item.lastUpdatedAt(), clock.instant(),
                item.institutionName(), item.institutionImageUrl(), item.consentExpiresAt());
        return result;
    }
}
