package com.wallet.bff.model.dto.response;

import java.time.LocalDate;
import java.util.List;

public record NetWorthResponse(List<Point> points) {

    public record Point(LocalDate date, String netWorth, String cash, String investments, String creditCardDebt) {
    }
}
