package com.wallet.core.banking.mapper;

import com.wallet.core.banking.AccountSummary;
import com.wallet.core.banking.CardBill;
import com.wallet.core.banking.CardBillItem;
import com.wallet.core.banking.dto.AccountResponse;
import com.wallet.core.banking.dto.BillResponse;
import com.wallet.core.banking.dto.CreditCardResponse;
import com.wallet.core.banking.dto.TransactionResponse;
import com.wallet.core.banking.entity.Account;
import com.wallet.core.banking.entity.AccountTransaction;
import com.wallet.core.banking.entity.CreditCardBill;
import com.wallet.core.shared.finance.Direction;
import com.wallet.core.shared.money.Money;
import lombok.experimental.UtilityClass;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

import static java.util.Objects.isNull;

@UtilityClass
public class BankingReadMapper {

    /** Purchases first, largest first, then refunds: what weighs most on the bill on top. */
    private static final Comparator<CardBillItem> HEAVIEST_FIRST = Comparator
            .comparing((CardBillItem item) -> Direction.INFLOW.equals(item.direction()))
            .thenComparing(CardBillItem::amount, Comparator.reverseOrder())
            .thenComparing(CardBillItem::date)
            .thenComparing(CardBillItem::transactionId);

    public AccountResponse toResponse(Account account) {
        return new AccountResponse(account.getId(), account.getConnectionId(), account.getKind(), account.getName(),
                account.getNumberLastDigits(), account.getCurrencyCode(), account.getBalance(),
                account.getCreditLimit(), account.getAvailableCredit(), account.getUpdatedAt());
    }

    public AccountSummary toSummary(Account account) {
        return new AccountSummary(account.getId(), account.getConnectionId(), account.getKind(), account.getName(),
                account.getNumberLastDigits(), account.getCurrencyCode(), account.getBalance(),
                account.getCreditLimit(), account.getAvailableCredit());
    }

    public TransactionResponse toResponse(AccountTransaction transaction) {
        return new TransactionResponse(transaction.getId(), transaction.getAccountId(), transaction.getBookedOn(),
                transaction.getDescription(), transaction.getAmount(), transaction.getDirection(),
                transaction.getStatus(), transaction.getCategory(), transaction.getInstallmentNumber(),
                transaction.getInstallmentTotal());
    }

    public BillResponse toResponse(CreditCardBill bill) {
        if (isNull(bill)) {
            return null;
        }
        return new BillResponse(bill.getId(), bill.getDueDate(), bill.getClosingDate(), bill.getTotalAmount(),
                bill.getMinimumPayment(), bill.getCurrencyCode());
    }

    public CreditCardResponse toCardResponse(Account card, CreditCardBill currentBill) {
        return new CreditCardResponse(card.getId(), card.getConnectionId(), card.getName(), card.getNumberLastDigits(),
                card.getCurrencyCode(), card.getCreditLimit(), card.getAvailableCredit(), card.getBalance(),
                toResponse(currentBill));
    }

    public CardBill toCardBill(Account card, LocalDate dueDate, LocalDate closingDate, Money total, boolean open,
                               List<AccountTransaction> items) {
        return new CardBill(card.getId(), card.getConnectionId(), card.getName(), card.getNumberLastDigits(), dueDate,
                closingDate, total, open, items.stream().map(BankingReadMapper::toBillItem).sorted(HEAVIEST_FIRST).toList());
    }

    public CardBillItem toBillItem(AccountTransaction transaction) {
        return new CardBillItem(transaction.getId(), transaction.getBookedOn(), transaction.getDescription(),
                transaction.getAmount(), transaction.getDirection(), transaction.getCategory(),
                transaction.getInstallmentNumber(), transaction.getInstallmentTotal());
    }
}
