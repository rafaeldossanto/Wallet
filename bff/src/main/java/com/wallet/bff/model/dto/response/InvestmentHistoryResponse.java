package com.wallet.bff.model.dto.response;

import java.time.LocalDate;
import java.util.List;

/** The invested total day by day over a period ({@code 1M}, {@code 3M}, {@code 6M}, {@code 1A}, {@code TUDO}). */
public record InvestmentHistoryResponse(String period, LocalDate from, LocalDate to, List<Point> points) {

    public record Point(LocalDate date, String total) {
    }
}
