package com.wallet.core.banking;

import com.wallet.core.shared.finance.Direction;
import com.wallet.core.shared.finance.TransactionStatus;
import com.wallet.core.shared.money.Money;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Everything the banking module holds about one user, for the export of their data (LGPD, art. 18,
 * II and V). Transaction descriptions are decrypted; transactions the provider no longer reports
 * are left out, as they are in the app. The field names are the export's schema: renaming one is a
 * new {@code schemaVersion} of the export.
 */
public record BankingExport(
        List<AccountSummary> accounts,
        List<Balance> balances,
        List<Transaction> transactions,
        List<Bill> bills) {

    /** An account's balance at the end of a day. */
    public record Balance(UUID accountId, LocalDate date, Money balance) {
    }

    public record Transaction(
            UUID id,
            UUID accountId,
            LocalDate bookedOn,
            String description,
            Money amount,
            Direction direction,
            TransactionStatus status,
            String category,
            Integer installmentNumber,
            Integer installmentTotal) {
    }

    /** A credit card bill; its purchases are among the transactions. */
    public record Bill(
            UUID id,
            UUID accountId,
            LocalDate dueDate,
            LocalDate closingDate,
            Money total,
            Money minimumPayment,
            String currencyCode) {
    }
}
