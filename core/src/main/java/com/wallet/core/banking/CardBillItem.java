package com.wallet.core.banking;

import com.wallet.core.shared.finance.Direction;
import com.wallet.core.shared.money.Money;

import java.time.LocalDate;
import java.util.UUID;

/** A purchase on a bill ({@code OUTFLOW}), or a refund that lowers it ({@code INFLOW}). */
public record CardBillItem(
        UUID transactionId,
        LocalDate date,
        String description,
        Money amount,
        Direction direction,
        String category,
        Integer installmentNumber,
        Integer installmentTotal) {
}
