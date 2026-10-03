package com.wallet.bff.controller;

import com.wallet.bff.auth.CurrentUserId;
import com.wallet.bff.model.dto.response.CalendarResponse;
import com.wallet.bff.model.dto.response.DaySpendingResponse;
import com.wallet.bff.service.CalendarService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class CalendarController {

    private final CalendarService calendarService;

    /** {@code month=2026-10}; the core validates it and defaults to the current month. */
    @GetMapping("/api/calendar")
    public CalendarResponse month(@CurrentUserId UUID userId, @RequestParam(required = false) String month) {
        return calendarService.month(userId, month);
    }

    @GetMapping("/api/calendar/{date}")
    public DaySpendingResponse day(@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return calendarService.day(date);
    }
}
