package com.wallet.bff.model.dto.response;

import java.time.LocalDate;
import java.util.List;

/** The month's spending day by day (core's daily spending); days without spending are absent. */
public record CalendarResponse(String month, String total, List<Day> days) {

    public record Day(LocalDate date, String total, int count) {
    }
}
