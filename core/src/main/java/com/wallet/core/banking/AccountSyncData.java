package com.wallet.core.banking;

import com.wallet.core.provider.ProviderAccount;
import com.wallet.core.provider.ProviderBill;
import com.wallet.core.provider.ProviderTransaction;

import java.time.LocalDate;
import java.util.List;

/**
 * Everything the sync read from the provider for one account.
 *
 * @param windowFrom first day asked for: local transactions in the window that did not come
 *                   back are soft-deleted, because the provider may have recreated them with a new id
 */
public record AccountSyncData(
        ProviderAccount account,
        LocalDate windowFrom,
        LocalDate windowTo,
        List<ProviderTransaction> transactions,
        List<ProviderBill> bills) {
}
