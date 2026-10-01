package com.wallet.bff.model.dto.response;

/** The insights screen in one call: the month's spending and the last six months of net worth. */
public record InsightsResponse(SpendingResponse spending, NetWorthResponse netWorth) {
}
