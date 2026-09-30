package com.wallet.core.shared.finance;

public enum InvestmentKind {
    /** CDB, LCI, LCA, CRI, CRA, debentures. */
    FIXED_INCOME,
    /** Tesouro Direto. */
    TREASURY,
    FUND,
    /** Stocks, FIIs, ETFs, BDRs. */
    EQUITY,
    /** Previdência. */
    RETIREMENT,
    OTHER
}
