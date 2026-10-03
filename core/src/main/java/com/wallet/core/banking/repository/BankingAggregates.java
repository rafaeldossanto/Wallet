package com.wallet.core.banking.repository;

import com.wallet.core.banking.CategorySpending;
import com.wallet.core.banking.DailySpending;
import com.wallet.core.shared.finance.AccountKind;
import com.wallet.core.shared.finance.Direction;
import com.wallet.core.shared.money.Money;
import com.wallet.core.shared.time.DateRange;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static java.util.Objects.isNull;

/**
 * Sums in plain SQL. JPQL {@code sum()} over a converted {@code Money} attribute has no clear
 * result type, and these queries are simpler to read as SQL anyway.
 */
@Repository
@RequiredArgsConstructor
public class BankingAggregates {

    /** Pluggy's category for paying a card bill from a bank account. To confirm in the T02 spike. */
    public static final String CARD_PAYMENT_CATEGORY = "credit card payment";

    private final JdbcTemplate jdbc;

    public Money bankTotal(UUID userId, Direction direction, DateRange period) {
        BigDecimal total = jdbc.queryForObject("""
                SELECT COALESCE(SUM(t.amount), 0)
                  FROM transactions t JOIN accounts a ON a.id = t.account_id
                 WHERE t.user_id = ? AND t.deleted_at IS NULL AND a.kind <> 'CREDIT_CARD'
                   AND t.direction = ? AND t.booked_on BETWEEN ? AND ?
                """, BigDecimal.class, userId, direction.name(), Date.valueOf(period.from()), Date.valueOf(period.to()));
        return isNull(total) ? Money.ZERO : Money.of(total);
    }

    public List<CategorySpending> spendingByCategory(UUID userId, DateRange period) {
        return jdbc.query("""
                SELECT COALESCE(t.category, 'UNCATEGORIZED') AS category, SUM(t.amount) AS total
                  FROM transactions t JOIN accounts a ON a.id = t.account_id
                 WHERE t.user_id = ? AND t.deleted_at IS NULL AND t.direction = 'OUTFLOW'
                   AND t.booked_on BETWEEN ? AND ?
                   AND NOT (a.kind <> 'CREDIT_CARD' AND LOWER(COALESCE(t.category, '')) = ?)
                 GROUP BY COALESCE(t.category, 'UNCATEGORIZED')
                 ORDER BY total DESC, category
                """,
                (row, index) -> new CategorySpending(row.getString("category"), Money.of(row.getBigDecimal("total"))),
                userId, Date.valueOf(period.from()), Date.valueOf(period.to()), CARD_PAYMENT_CATEGORY);
    }

    public List<DailySpending> spendingByDay(UUID userId, DateRange period) {
        return jdbc.query("""
                SELECT t.booked_on, SUM(t.amount) AS total, COUNT(*) AS count
                  FROM transactions t JOIN accounts a ON a.id = t.account_id
                 WHERE t.user_id = ? AND t.deleted_at IS NULL AND t.direction = 'OUTFLOW'
                   AND t.booked_on BETWEEN ? AND ?
                   AND NOT (a.kind <> 'CREDIT_CARD' AND LOWER(COALESCE(t.category, '')) = ?)
                 GROUP BY t.booked_on
                 ORDER BY t.booked_on
                """,
                (row, index) -> new DailySpending(row.getDate("booked_on").toLocalDate(),
                        Money.of(row.getBigDecimal("total")), row.getInt("count")),
                userId, Date.valueOf(period.from()), Date.valueOf(period.to()), CARD_PAYMENT_CATEGORY);
    }

    /** Every snapshot up to {@code to}, oldest first: the ones before the period seed the first day. */
    public List<SnapshotRow> snapshotsUpTo(UUID userId, LocalDate to) {
        return jdbc.query("""
                SELECT s.account_id, a.kind, s.snapshot_date, s.balance
                  FROM balance_snapshots s JOIN accounts a ON a.id = s.account_id
                 WHERE a.user_id = ? AND s.snapshot_date <= ?
                 ORDER BY s.snapshot_date
                """,
                (row, index) -> new SnapshotRow(
                        row.getObject("account_id", UUID.class),
                        AccountKind.valueOf(row.getString("kind")),
                        row.getDate("snapshot_date").toLocalDate(),
                        Money.of(row.getBigDecimal("balance"))),
                userId, Date.valueOf(to));
    }

    public record SnapshotRow(UUID accountId, AccountKind kind, LocalDate date, Money balance) {
    }
}
