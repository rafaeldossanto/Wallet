package com.wallet.core.provider.pluggy;

import com.wallet.core.provider.FinancialDataProvider;
import com.wallet.core.provider.ProviderAccount;
import com.wallet.core.provider.ProviderBill;
import com.wallet.core.provider.ProviderInvestment;
import com.wallet.core.provider.ProviderItem;
import com.wallet.core.provider.ProviderTransaction;
import com.wallet.core.shared.finance.AccountKind;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

@Component
class PluggyFinancialDataProvider implements FinancialDataProvider {

    private final PluggyClient client;

    PluggyFinancialDataProvider(PluggyClient client) {
        this.client = client;
    }

    @Override
    public ProviderItem findItem(String itemId) {
        return PluggyMapper.toItem(client.getItem(itemId));
    }

    @Override
    public List<ProviderAccount> listAccounts(String itemId) {
        return client.getAccounts(itemId).stream().map(PluggyMapper::toAccount).toList();
    }

    @Override
    public List<ProviderTransaction> listTransactions(String accountId, AccountKind accountKind,
                                                      LocalDate from, LocalDate to) {
        return client.getTransactions(accountId, from, to).stream()
                .map(transaction -> PluggyMapper.toTransaction(transaction, accountKind))
                .toList();
    }

    @Override
    public List<ProviderBill> listBills(String accountId) {
        return client.getBills(accountId).stream().map(bill -> PluggyMapper.toBill(bill, accountId)).toList();
    }

    @Override
    public List<ProviderInvestment> listInvestments(String itemId) {
        return client.getInvestments(itemId).stream().map(PluggyMapper::toInvestment).toList();
    }

    @Override
    public void deleteItem(String itemId) {
        client.deleteItem(itemId);
    }
}
