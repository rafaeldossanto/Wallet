package com.wallet.core.banking;

public record BankingSyncResult(int accounts, int transactionsUpserted, int transactionsDeleted) {
}
