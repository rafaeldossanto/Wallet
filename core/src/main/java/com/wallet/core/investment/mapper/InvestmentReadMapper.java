package com.wallet.core.investment.mapper;

import com.wallet.core.investment.PositionSummary;
import com.wallet.core.investment.dto.PositionResponse;
import com.wallet.core.investment.entity.Investment;
import lombok.experimental.UtilityClass;

@UtilityClass
public class InvestmentReadMapper {

    public PositionSummary toSummary(Investment investment) {
        return new PositionSummary(investment.getId(), investment.getConnectionId(), investment.getKind(),
                investment.getSubtype(), investment.getName(), investment.getCurrencyCode(), investment.getBalance(),
                investment.getAmountInvested(), investment.getDueDate());
    }

    public PositionResponse toResponse(Investment investment) {
        return new PositionResponse(investment.getId(), investment.getConnectionId(), investment.getKind(),
                investment.getSubtype(), investment.getName(), investment.getCurrencyCode(), investment.getBalance(),
                investment.getAmountInvested(), investment.getDueDate());
    }
}
