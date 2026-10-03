package com.wallet.bff.model.dto.response;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * What was spent on one day, each item with the account and the bank it came from. The day's
 * total is the calendar's: the BFF does no arithmetic with money.
 */
public record DaySpendingResponse(LocalDate date, List<Item> items) {

    public record Item(
            UUID id,
            UUID accountId,
            String accountName,
            String institutionName,
            String institutionImageUrl,
            String description,
            String amount,
            String status,
            String category,
            Integer installmentNumber,
            Integer installmentTotal) {
    }
}
