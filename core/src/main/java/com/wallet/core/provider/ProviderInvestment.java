package com.wallet.core.provider;

import com.wallet.core.shared.finance.InvestmentKind;
import com.wallet.core.shared.money.Money;

import java.time.LocalDate;

/**
 * @param balance        current net value, what the user would see as "tenho aqui"
 * @param amountInvested original amount put in, when the institution reports it
 * @param active         false once the position was fully withdrawn
 */
public record ProviderInvestment(
        String id,
        String itemId,
        InvestmentKind kind,
        String subtype,
        String name,
        String currencyCode,
        Money balance,
        Money amountInvested,
        LocalDate dueDate,
        boolean active) {
}
