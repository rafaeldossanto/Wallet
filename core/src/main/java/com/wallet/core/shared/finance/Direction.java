package com.wallet.core.shared.finance;

/**
 * Whether money came in or went out. The Wallet always stores a positive amount plus a
 * direction, because providers disagree on signs (a credit card charge is positive at Pluggy,
 * a checking account debit is not).
 */
public enum Direction {
    INFLOW,
    OUTFLOW
}
