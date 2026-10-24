package com.wallet.core.investment.mapper;

import com.wallet.core.investment.InvestmentExport;
import com.wallet.core.investment.PositionSummary;
import com.wallet.core.investment.dto.PositionResponse;
import com.wallet.core.investment.entity.Investment;
import com.wallet.core.investment.entity.InvestmentSnapshot;
import lombok.experimental.UtilityClass;

import java.util.List;

@UtilityClass
public class InvestmentReadMapper {

    public PositionSummary toSummary(Investment investment) {
        return new PositionSummary(investment.getId(), investment.getConnectionId(), investment.getKind(),
                investment.getSubtype(), investment.getName(), investment.getCurrencyCode(), investment.getBalance(),
                investment.getAmountInvested(), investment.getDueDate());
    }

    public InvestmentExport toExport(List<Investment> investments, List<InvestmentSnapshot> snapshots) {
        return new InvestmentExport(
                investments.stream()
                        .map(investment -> new InvestmentExport.Position(investment.getId(), investment.getConnectionId(),
                                investment.getKind(), investment.getSubtype(), investment.getName(),
                                investment.getCurrencyCode(), investment.getBalance(), investment.getAmountInvested(),
                                investment.getDueDate(), investment.getClosedAt()))
                        .toList(),
                snapshots.stream()
                        .map(snapshot -> new InvestmentExport.Balance(snapshot.getInvestmentId(), snapshot.getSnapshotDate(),
                                snapshot.getBalance()))
                        .toList());
    }

    public PositionResponse toResponse(Investment investment) {
        return new PositionResponse(investment.getId(), investment.getConnectionId(), investment.getKind(),
                investment.getSubtype(), investment.getName(), investment.getCurrencyCode(), investment.getBalance(),
                investment.getAmountInvested(), investment.getDueDate());
    }
}
