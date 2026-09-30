package com.wallet.core.banking.dto;

import com.wallet.core.shared.finance.Direction;
import com.wallet.core.shared.finance.TransactionStatus;
import com.wallet.core.shared.money.Money;

import java.time.LocalDate;
import java.util.UUID;

public record TransactionResponse(
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
