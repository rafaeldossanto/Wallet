package com.wallet.core.banking.mapper;

import com.wallet.core.banking.AccountSummary;
import com.wallet.core.banking.dto.AccountResponse;
import com.wallet.core.banking.dto.BillResponse;
import com.wallet.core.banking.dto.CreditCardResponse;
import com.wallet.core.banking.dto.TransactionResponse;
import com.wallet.core.banking.entity.Account;
import com.wallet.core.banking.entity.AccountTransaction;
import com.wallet.core.banking.entity.CreditCardBill;
import lombok.experimental.UtilityClass;

import static java.util.Objects.isNull;

@UtilityClass
public class BankingReadMapper {

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
}
