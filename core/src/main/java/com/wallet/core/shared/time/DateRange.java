package com.wallet.core.shared.time;

import com.wallet.core.shared.error.DomainException;
import com.wallet.core.shared.error.ErrorType;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import static java.util.Objects.isNull;

/** An inclusive range of calendar days, validated once where it enters the system. */
public record DateRange(LocalDate from, LocalDate to) {

    /**
     * Missing ends take the defaults. A reversed range is {@code period.invalid}; one longer than
     * {@code maxDays} is {@code period.too_long} (it bounds the in-memory text search).
     */
    public static DateRange of(LocalDate from, LocalDate to, LocalDate defaultFrom, LocalDate defaultTo, int maxDays) {
        LocalDate start = isNull(from) ? defaultFrom : from;
        LocalDate end = isNull(to) ? defaultTo : to;
        if (start.isAfter(end)) {
            throw new DomainException(ErrorType.UNPROCESSABLE, "period.invalid", "from must not be after to");
        }
        if (ChronoUnit.DAYS.between(start, end) + 1 > maxDays) {
            throw new DomainException(ErrorType.UNPROCESSABLE, "period.too_long",
                    "The period can have at most " + maxDays + " days");
        }
        return new DateRange(start, end);
    }

    public List<LocalDate> days() {
        List<LocalDate> days = new ArrayList<>();
        for (LocalDate day = from; !day.isAfter(to); day = day.plusDays(1)) {
            days.add(day);
        }
        return days;
    }
}
