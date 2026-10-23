package com.wallet.core.investment.repository;

import com.wallet.core.shared.money.Money;
import com.wallet.core.shared.time.DateRange;
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

    /**
     * The period's snapshots plus, per position (closed ones included: their last snapshot is the
     * zero of the day they closed), the last one before it, oldest first: that one seeds the first
     * day. Never the whole history: one row per position from before the period, through the
     * primary key ({@code investment_id, snapshot_date}).
     */
    public List<SnapshotRow> snapshotsIn(UUID userId, DateRange period) {
        Date from = Date.valueOf(period.from());
        return jdbc.query("""
                SELECT i.id AS investment_id, seed.snapshot_date, seed.balance
                  FROM investments i
                  JOIN LATERAL (SELECT s.snapshot_date, s.balance
                                  FROM investment_snapshots s
                                 WHERE s.investment_id = i.id AND s.snapshot_date < ?
                                 ORDER BY s.snapshot_date DESC
                                 LIMIT 1) seed ON TRUE
                 WHERE i.user_id = ?
                UNION ALL
                SELECT s.investment_id, s.snapshot_date, s.balance
                  FROM investment_snapshots s JOIN investments i ON i.id = s.investment_id
                 WHERE i.user_id = ? AND s.snapshot_date BETWEEN ? AND ?
                 ORDER BY snapshot_date
                """,
                (row, index) -> new SnapshotRow(
                        row.getObject("investment_id", UUID.class),
                        row.getDate("snapshot_date").toLocalDate(),
                        Money.of(row.getBigDecimal("balance"))),
                from, userId, userId, from, Date.valueOf(period.to()));
    }

    public record SnapshotRow(UUID investmentId, LocalDate date, Money balance) {
    }
}
