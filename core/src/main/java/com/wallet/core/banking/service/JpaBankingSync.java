package com.wallet.core.banking.service;

import com.wallet.core.banking.AccountSyncData;
import com.wallet.core.banking.BankingSync;
import com.wallet.core.banking.BankingSyncResult;
import com.wallet.core.banking.entity.Account;
import com.wallet.core.banking.entity.AccountTransaction;
import com.wallet.core.banking.entity.BalanceSnapshot;
import com.wallet.core.banking.entity.CreditCardBill;
import com.wallet.core.banking.mapper.BankingSyncMapper;
import com.wallet.core.banking.repository.AccountRepository;
import com.wallet.core.banking.repository.AccountTransactionRepository;
import com.wallet.core.banking.repository.BalanceSnapshotRepository;
import com.wallet.core.banking.repository.CreditCardBillRepository;
import com.wallet.core.provider.ProviderBill;
import com.wallet.core.provider.ProviderTransaction;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.util.Objects.isNull;

@Service
@RequiredArgsConstructor
class JpaBankingSync implements BankingSync {

    private final AccountRepository accounts;
    private final BalanceSnapshotRepository snapshots;
    private final AccountTransactionRepository transactions;
    private final CreditCardBillRepository bills;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public Optional<LocalDate> latestBookedOn(UUID connectionId, String providerAccountId) {
        return accounts.findByConnectionIdAndProviderAccountId(connectionId, providerAccountId)
                .flatMap(account -> transactions.findLatestBookedOn(account.getId()));
    }

    @Override
    @Transactional
    public BankingSyncResult apply(UUID connectionId, UUID userId, LocalDate today, List<AccountSyncData> data) {
        Instant now = clock.instant();
        int upserted = 0;
        int deleted = 0;
        for (AccountSyncData accountData : data) {
            Account account = upsertAccount(connectionId, userId, accountData, now);
            snapshots.save(new BalanceSnapshot(account.getId(), today, account.getBalance()));
            WindowResult window = reconcileWindow(account, accountData, now);
            upserted += window.upserted();
            deleted += window.deleted();
            upsertBills(account, accountData.bills(), now);
        }
        return new BankingSyncResult(data.size(), upserted, deleted);
    }

    private Account upsertAccount(UUID connectionId, UUID userId, AccountSyncData data, Instant now) {
        Account account = accounts.findByConnectionIdAndProviderAccountId(connectionId, data.account().id())
                .map(existing -> {
                    BankingSyncMapper.copy(data.account(), existing, now);
                    return existing;
                })
                .orElseGet(() -> BankingSyncMapper.newAccount(connectionId, userId, data.account(), now));
        return accounts.save(account);
    }

    /**
     * Upserts what came back and soft-deletes what did not: inside the window, the provider's list
     * is the truth. A transaction whose id changed shows up as one deletion plus one insertion.
     */
    private WindowResult reconcileWindow(Account account, AccountSyncData data, Instant now) {
        Map<String, AccountTransaction> local = transactions
                .findByAccountIdAndBookedOnBetween(account.getId(), data.windowFrom(), data.windowTo()).stream()
                .collect(Collectors.toMap(AccountTransaction::getProviderTransactionId, Function.identity()));

        Map<String, ProviderTransaction> remote = new LinkedHashMap<>();
        data.transactions().forEach(transaction -> remote.put(transaction.id(), transaction));

        for (ProviderTransaction source : remote.values()) {
            AccountTransaction target = local.remove(source.id());
            if (isNull(target)) {
                // Outside the local window (a date shifted by the provider) or brand new.
                target = transactions.findByAccountIdAndProviderTransactionId(account.getId(), source.id())
                        .orElse(null);
            }
            if (isNull(target)) {
                target = BankingSyncMapper.newTransaction(account.getId(), account.getUserId(), source, now);
            } else {
                BankingSyncMapper.copy(source, target, now);
            }
            transactions.save(target);
        }

        int deleted = 0;
        for (AccountTransaction missing : local.values()) {
            if (isNull(missing.getDeletedAt())) {
                missing.setDeletedAt(now);
                missing.setUpdatedAt(now);
                deleted++;
            }
        }
        return new WindowResult(remote.size(), deleted);
    }

    private void upsertBills(Account account, List<ProviderBill> sourceBills, Instant now) {
        Map<String, CreditCardBill> existing = bills.findByAccountId(account.getId()).stream()
                .collect(Collectors.toMap(CreditCardBill::getProviderBillId, Function.identity()));
        for (ProviderBill source : sourceBills) {
            CreditCardBill bill = existing.get(source.id());
            if (isNull(bill)) {
                bills.save(BankingSyncMapper.newBill(account.getId(), source, now));
            } else {
                BankingSyncMapper.copy(source, bill, now);
            }
        }
    }

    private record WindowResult(int upserted, int deleted) {
    }
}
