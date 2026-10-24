package com.wallet.core.investment;

import com.wallet.core.shared.finance.InvestmentKind;
import com.wallet.core.shared.money.Money;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Everything the investment module holds about one user, for the export of their data (LGPD,
 * art. 18, II and V): positions, the closed ones too, and each one's balance over time. The field
 * names are the export's schema: renaming one is a new {@code schemaVersion} of the export.
 */
public record InvestmentExport(List<Position> positions, List<Balance> balances) {

    /** @param closedAt fully withdrawn, or no longer reported by the provider; {@code null} while open */
    public record Position(
            UUID id,
            UUID connectionId,
            InvestmentKind kind,
            String subtype,
            String name,
            String currencyCode,
            Money balance,
            Money amountInvested,
            LocalDate dueDate,
            Instant closedAt) {
    }

    /** A position's balance at the end of a day. */
    public record Balance(UUID investmentId, LocalDate date, Money balance) {
    }
}
