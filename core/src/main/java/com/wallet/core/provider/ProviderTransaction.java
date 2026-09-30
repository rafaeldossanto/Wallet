package com.wallet.core.provider;

import com.wallet.core.shared.finance.Direction;
import com.wallet.core.shared.finance.TransactionStatus;
import com.wallet.core.shared.money.Money;

import java.time.LocalDate;

/**
 * @param id          may change when the bank changes the transaction a lot (the provider deletes
 *                    and recreates it), which is why the sync reconciles a window
 * @param reconcileId the institution's own id when Open Finance gives one; more stable than {@code id}
 * @param amount      always positive; {@code direction} says in or out
 */
public record ProviderTransaction(
        String id,
        String accountId,
        String reconcileId,
        LocalDate bookedOn,
        String description,
        Money amount,
        Direction direction,
        TransactionStatus status,
        String category,
        Integer installmentNumber,
        Integer installmentTotal,
        String billId) {
}
