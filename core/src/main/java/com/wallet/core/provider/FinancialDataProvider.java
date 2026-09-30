package com.wallet.core.provider;

import com.wallet.core.shared.finance.AccountKind;

import java.time.LocalDate;
import java.util.List;

/**
 * Port to whoever holds the Open Finance license (Pluggy today). The rest of the core only sees
 * these Wallet records, so switching to Belvo or Celcoin means writing a new adapter, nothing else.
 *
 * <p>Failures come out as {@link com.wallet.core.shared.error.DomainException} with the codes in
 * {@link ProviderErrors}.
 */
public interface FinancialDataProvider {

    ProviderItem findItem(String itemId);

    List<ProviderAccount> listAccounts(String itemId);

    /**
     * @param accountKind needed because the sign convention differs between checking accounts
     *                    and credit cards; the returned amounts are always positive
     */
    List<ProviderTransaction> listTransactions(String accountId, AccountKind accountKind, LocalDate from, LocalDate to);

    List<ProviderBill> listBills(String accountId);

    List<ProviderInvestment> listInvestments(String itemId);

    /** Revokes the connection at the provider (and the Open Finance consent with it). */
    void deleteItem(String itemId);
}
