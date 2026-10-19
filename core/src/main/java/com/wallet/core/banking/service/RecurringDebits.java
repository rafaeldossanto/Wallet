package com.wallet.core.banking.service;

import com.wallet.core.banking.RecurringDebit;
import com.wallet.core.banking.entity.AccountTransaction;
import com.wallet.core.banking.mapper.BankingReadMapper;
import com.wallet.core.shared.finance.Direction;
import com.wallet.core.shared.money.Money;
import com.wallet.core.shared.text.TextKeys;
import com.wallet.core.shared.time.DateRange;
import lombok.experimental.UtilityClass;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static java.util.Objects.isNull;

/**
 * Which bank debits repeat every month. Debits with the same description key are candidates;
 * starting from the latest, each earlier month needs one about the same day of the month with a
 * similar amount. What a series leaves out of the group may start another one, so two boletos with
 * the same generic description, on different days, are two series.
 */
@UtilityClass
class RecurringDebits {

    /** A debit due on the 10th may leave on the 7th or the 13th: weekends, holidays, the bank's own pace. */
    static final int DAY_TOLERANCE = 3;

    /** Power and water swing with the season; still, no month is more than twice another. */
    static final int MAX_AMOUNT_RATIO = 2;

    /** More than this a month, on average, is a habit (groceries, rides), not a bill. */
    static final int MAX_PER_MONTH = 2;

    private static final Comparator<AccountTransaction> NEWEST_FIRST = Comparator
            .comparing(AccountTransaction::getBookedOn, Comparator.reverseOrder())
            .thenComparing(AccountTransaction::getId);

    /**
     * @param outflows  debits of bank accounts; anything else (inflows, deleted, card bill
     *                  payments, outside the period) is ignored
     * @param period    the months looked at, up to today. A series whose debit of the next month
     *                  is overdue by more than {@value #DAY_TOLERANCE} days has stopped
     * @param minMonths in how many months a series must show up
     * @return by day of the month
     */
    static List<RecurringDebit> find(Collection<AccountTransaction> outflows, DateRange period, int minMonths) {
        Map<String, List<AccountTransaction>> byKey = new LinkedHashMap<>();
        for (AccountTransaction transaction : outflows) {
            if (counts(transaction, period)) {
                byKey.computeIfAbsent(TextKeys.key(transaction.getDescription()), key -> new ArrayList<>()).add(transaction);
            }
        }
        byKey.remove("");
        List<RecurringDebit> found = new ArrayList<>();
        byKey.forEach((key, group) -> {
            if (!tooFrequent(group)) {
                found.addAll(series(key, group, period, minMonths));
            }
        });
        found.sort(Comparator.comparingInt(RecurringDebit::dayOfMonth)
                .thenComparing(RecurringDebit::descriptionKey)
                .thenComparing(RecurringDebit::amount, Comparator.reverseOrder()));
        return found;
    }

    private static boolean counts(AccountTransaction transaction, DateRange period) {
        return isNull(transaction.getDeletedAt())
                && Direction.OUTFLOW.equals(transaction.getDirection())
                && transaction.getAmount().isPositive()
                && !BillCycles.isBillPayment(transaction)
                && !transaction.getBookedOn().isBefore(period.from())
                && !transaction.getBookedOn().isAfter(period.to());
    }

    private static boolean tooFrequent(List<AccountTransaction> group) {
        long months = group.stream().map(transaction -> YearMonth.from(transaction.getBookedOn())).distinct().count();
        return group.size() > months * MAX_PER_MONTH;
    }

    private static List<RecurringDebit> series(String key, List<AccountTransaction> group, DateRange period, int minMonths) {
        List<AccountTransaction> left = new ArrayList<>(group);
        left.sort(NEWEST_FIRST);
        YearMonth first = YearMonth.from(period.from());
        List<RecurringDebit> found = new ArrayList<>();
        while (!left.isEmpty()) {
            AccountTransaction latest = left.removeFirst();
            int day = latest.getBookedOn().getDayOfMonth();
            List<AccountTransaction> series = new ArrayList<>(List.of(latest));
            for (YearMonth month = YearMonth.from(latest.getBookedOn()).minusMonths(1); !month.isBefore(first);
                 month = month.minusMonths(1)) {
                matchFor(left, sameDay(month, day), latest.getAmount()).ifPresent(match -> {
                    series.add(match);
                    left.remove(match);
                });
            }
            if (series.size() >= minMonths && !stopped(latest, period.to())) {
                found.add(toDebit(key, series));
            }
        }
        return found;
    }

    /** The debit closest to {@code expected}, within the tolerance, with an amount similar to the latest one. */
    private static Optional<AccountTransaction> matchFor(List<AccountTransaction> left, LocalDate expected, Money amount) {
        return left.stream()
                .filter(transaction -> daysBetween(expected, transaction) <= DAY_TOLERANCE)
                .filter(transaction -> similar(transaction.getAmount(), amount))
                .min(Comparator.comparingLong((AccountTransaction transaction) -> daysBetween(expected, transaction))
                        .thenComparing(transaction -> transaction.getAmount().minus(amount).abs())
                        .thenComparing(NEWEST_FIRST));
    }

    private static long daysBetween(LocalDate expected, AccountTransaction transaction) {
        return Math.abs(ChronoUnit.DAYS.between(expected, transaction.getBookedOn()));
    }

    private static boolean similar(Money one, Money other) {
        Money larger = one.compareTo(other) >= 0 ? one : other;
        Money smaller = larger == one ? other : one;
        return larger.compareTo(smaller.times(MAX_AMOUNT_RATIO)) <= 0;
    }

    /** The next debit after {@code latest} should have left by now, and did not: cancelled or moved elsewhere. */
    private static boolean stopped(AccountTransaction latest, LocalDate today) {
        LocalDate booked = latest.getBookedOn();
        return sameDay(YearMonth.from(booked).plusMonths(1), booked.getDayOfMonth()).plusDays(DAY_TOLERANCE).isBefore(today);
    }

    /** The day in {@code month}: the 31st of a 30-day month is the 30th. */
    private static LocalDate sameDay(YearMonth month, int day) {
        return month.atDay(Math.min(day, month.lengthOfMonth()));
    }

    /** @param series the latest debit first */
    private static RecurringDebit toDebit(String key, List<AccountTransaction> series) {
        Money total = series.stream().map(AccountTransaction::getAmount).reduce(Money.ZERO, Money::plus);
        return BankingReadMapper.toRecurringDebit(series.getFirst(), key, usualDay(series), total.dividedBy(series.size()),
                series.size());
    }

    /** The most common day of the month; on a tie, the latest debit's. */
    private static int usualDay(List<AccountTransaction> series) {
        Map<Integer, Integer> counts = new HashMap<>();
        series.forEach(transaction -> counts.merge(transaction.getBookedOn().getDayOfMonth(), 1, Integer::sum));
        int usual = series.getFirst().getBookedOn().getDayOfMonth();
        for (AccountTransaction transaction : series) {
            int day = transaction.getBookedOn().getDayOfMonth();
            if (counts.get(day) > counts.get(usual)) {
                usual = day;
            }
        }
        return usual;
    }
}
