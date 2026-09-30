package com.wallet.core.investment;

import com.wallet.core.provider.ProviderInvestment;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** The investment module's API for the sync. */
public interface InvestmentSync {

    /**
     * Replaces the connection's positions with the provider's list: new ones are added, known ones
     * updated, and positions that disappeared or were fully withdrawn are closed. Records today's
     * value of each position (zero for the ones closed today). Joins the caller's transaction.
     * Returns how many positions are open afterwards.
     */
    int apply(UUID connectionId, UUID userId, LocalDate today, List<ProviderInvestment> investments);
}
