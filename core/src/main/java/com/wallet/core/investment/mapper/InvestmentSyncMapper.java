package com.wallet.core.investment.mapper;

import com.wallet.core.investment.entity.Investment;
import com.wallet.core.provider.ProviderInvestment;
import lombok.experimental.UtilityClass;

import java.time.Instant;
import java.util.UUID;

@UtilityClass
public class InvestmentSyncMapper {

    public Investment newInvestment(UUID connectionId, UUID userId, ProviderInvestment source, Instant now) {
        Investment investment = Investment.builder()
                .id(UUID.randomUUID())
                .connectionId(connectionId)
                .userId(userId)
                .providerInvestmentId(source.id())
                .createdAt(now)
                .build();
        copy(source, investment, now);
        return investment;
    }

    public void copy(ProviderInvestment source, Investment target, Instant now) {
        target.setKind(source.kind());
        target.setSubtype(source.subtype());
        target.setName(source.name());
        target.setCurrencyCode(source.currencyCode());
        target.setBalance(source.balance());
        target.setAmountInvested(source.amountInvested());
        target.setDueDate(source.dueDate());
        target.setClosedAt(source.active() ? null : now);
        target.setUpdatedAt(now);
    }
}
