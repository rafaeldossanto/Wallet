package com.wallet.core.banking;

import com.wallet.core.shared.money.Money;

/** @param category the provider's category; {@code UNCATEGORIZED} when it sent none */
public record CategorySpending(String category, Money total) {
}
