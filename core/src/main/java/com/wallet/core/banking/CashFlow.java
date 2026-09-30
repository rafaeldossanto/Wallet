package com.wallet.core.banking;

import com.wallet.core.shared.money.Money;

/**
 * Money that came into and left the bank accounts (checking, savings) in a period. Credit card
 * purchases are not here: they leave the bank when the bill is paid, and counting both would
 * count the same spending twice.
 */
public record CashFlow(Money income, Money expenses) {
}
