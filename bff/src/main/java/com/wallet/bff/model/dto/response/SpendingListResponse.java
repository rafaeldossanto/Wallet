package com.wallet.bff.model.dto.response;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * What was spent in a period (a day or a whole month), a page at a time, each item with the
 * account and the bank it came from. The period's total is the calendar's: the BFF does no
 * arithmetic with money.
 */
public record SpendingListResponse(LocalDate from, LocalDate to, int page, int totalPages, long total, List<Item> items) {

    public record Item(
            UUID id,
            UUID accountId,
            LocalDate bookedOn,
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
