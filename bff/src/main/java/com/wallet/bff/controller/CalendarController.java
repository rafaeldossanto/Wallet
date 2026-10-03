package com.wallet.bff.controller;

import com.wallet.bff.auth.CurrentUserId;
import com.wallet.bff.model.dto.response.CalendarResponse;
import com.wallet.bff.model.dto.response.SpendingListResponse;
import com.wallet.bff.service.CalendarService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
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

    /** The picked day ({@code from} = {@code to}) or the whole month; the core checks the period. */
    @GetMapping("/api/calendar/spending")
    public SpendingListResponse spending(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                         @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                         @RequestParam(defaultValue = "1") int page) {
        return calendarService.spending(from, to, page);
    }
}
