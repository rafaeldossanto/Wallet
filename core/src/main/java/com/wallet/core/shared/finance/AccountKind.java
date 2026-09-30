package com.wallet.core.shared.finance;

public enum AccountKind {
    CHECKING,
    SAVINGS,
    CREDIT_CARD,
    OTHER;

    public boolean isCreditCard() {
        return this == CREDIT_CARD;
    }
}
