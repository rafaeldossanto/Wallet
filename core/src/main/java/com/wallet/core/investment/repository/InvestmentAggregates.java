package com.wallet.core.investment.repository;

import com.wallet.core.shared.money.Money;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class InvestmentAggregates {

    private final JdbcTemplate jdbc;

    /** Every snapshot up to {@code to}, oldest first: the ones before the period seed the first day. */
    public List<SnapshotRow> snapshotsUpTo(UUID userId, LocalDate to) {
        return jdbc.query("""
                SELECT s.investment_id, s.snapshot_date, s.balance
                  FROM investment_snapshots s JOIN investments i ON i.id = s.investment_id
                 WHERE i.user_id = ? AND s.snapshot_date <= ?
                 ORDER BY s.snapshot_date
                """,
                (row, index) -> new SnapshotRow(
                        row.getObject("investment_id", UUID.class),
                        row.getDate("snapshot_date").toLocalDate(),
                        Money.of(row.getBigDecimal("balance"))),
                userId, Date.valueOf(to));
    }

    public record SnapshotRow(UUID investmentId, LocalDate date, Money balance) {
    }
}
