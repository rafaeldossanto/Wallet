package com.wallet.core.investment;

import com.wallet.core.shared.money.Money;
import com.wallet.core.shared.time.DateRange;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** The investment module's read API for the insight module. */
public interface InvestmentQueries {

    List<PositionSummary> openPositions(UUID userId);

    /** Total invested per day; a day without a snapshot repeats the last known value of each position. */
    Map<LocalDate, Money> totalHistory(UUID userId, DateRange period);
}
