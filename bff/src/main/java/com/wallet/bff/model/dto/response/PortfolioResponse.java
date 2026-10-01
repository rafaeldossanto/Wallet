package com.wallet.bff.model.dto.response;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record PortfolioResponse(String total, List<KindTotal> byKind, List<Position> positions) {

    public record KindTotal(String kind, String total) {
    }

    public record Position(UUID id, UUID connectionId, String kind, String subtype, String name,
                           String currencyCode, String balance, String amountInvested, LocalDate dueDate) {
    }
}
