package com.wallet.bff.model.dto.response;

import java.util.List;

public record SpendingResponse(String month, String total, List<CategoryTotal> categories) {

    public record CategoryTotal(String category, String total) {
    }
}
