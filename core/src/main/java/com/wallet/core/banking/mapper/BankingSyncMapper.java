package com.wallet.core.banking.mapper;

import com.wallet.core.banking.entity.Account;
import com.wallet.core.banking.entity.AccountTransaction;
import com.wallet.core.banking.entity.CreditCardBill;
import com.wallet.core.provider.ProviderAccount;
import com.wallet.core.provider.ProviderBill;
import com.wallet.core.provider.ProviderTransaction;
import lombok.experimental.UtilityClass;

import java.time.Instant;
import java.util.UUID;

/**
 * Copies provider data onto entities. {@code newX} generates the id (the Trilha convention),
 * {@code copy} updates an existing row in place so its id never changes.
 */
@UtilityClass
public class BankingSyncMapper {

    public Account newAccount(UUID connectionId, UUID userId, ProviderAccount source, Instant now) {
        Account account = Account.builder()
                .id(UUID.randomUUID())
                .connectionId(connectionId)
                .userId(userId)
                .providerAccountId(source.id())
                .createdAt(now)
                .build();
        copy(source, account, now);
        return account;
    }

    public void copy(ProviderAccount source, Account target, Instant now) {
        target.setKind(source.kind());
        target.setName(source.name());
        target.setNumberLastDigits(source.numberLastDigits());
        target.setCurrencyCode(source.currencyCode());
        target.setBalance(source.balance());
        target.setCreditLimit(source.creditLimit());
        target.setAvailableCredit(source.availableCredit());
        target.setUpdatedAt(now);
    }

    public AccountTransaction newTransaction(UUID accountId, UUID userId, ProviderTransaction source, Instant now) {
        AccountTransaction transaction = AccountTransaction.builder()
                .id(UUID.randomUUID())
                .accountId(accountId)
                .userId(userId)
                .providerTransactionId(source.id())
                .createdAt(now)
                .build();
        copy(source, transaction, now);
        return transaction;
    }

    /** Also revives a transaction that had been soft-deleted and came back. */
    public void copy(ProviderTransaction source, AccountTransaction target, Instant now) {
        target.setProviderReconcileId(source.reconcileId());
        target.setBookedOn(source.bookedOn());
        target.setDescription(source.description());
        target.setAmount(source.amount());
        target.setDirection(source.direction());
        target.setStatus(source.status());
        target.setCategory(source.category());
        target.setInstallmentNumber(source.installmentNumber());
        target.setInstallmentTotal(source.installmentTotal());
        target.setProviderBillId(source.billId());
        target.setDeletedAt(null);
        target.setUpdatedAt(now);
    }

    public CreditCardBill newBill(UUID accountId, ProviderBill source, Instant now) {
        CreditCardBill bill = CreditCardBill.builder()
                .id(UUID.randomUUID())
                .accountId(accountId)
                .providerBillId(source.id())
                .createdAt(now)
                .build();
        copy(source, bill, now);
        return bill;
    }

    public void copy(ProviderBill source, CreditCardBill target, Instant now) {
        target.setDueDate(source.dueDate());
        target.setClosingDate(source.closingDate());
        target.setTotalAmount(source.totalAmount());
        target.setMinimumPayment(source.minimumPayment());
        target.setCurrencyCode(source.currencyCode());
        target.setUpdatedAt(now);
    }
}
