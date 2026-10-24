package com.wallet.core.privacy.dto;

import com.wallet.core.banking.BankingExport;
import com.wallet.core.connection.ConnectionSummary;
import com.wallet.core.identity.UserProfile;
import com.wallet.core.investment.InvestmentExport;
import com.wallet.core.planning.PlanningExport;

import java.time.Instant;
import java.util.List;

/**
 * Everything the Wallet holds about the user (LGPD, art. 18, II and V): a file they can read and
 * take elsewhere. Money is a string, as in the rest of the API. Never a password hash, a
 * second-factor secret or a token.
 *
 * <p>Each module's part keeps the field names of its own export record. They are the file's schema:
 * adding a field keeps {@code schemaVersion}, renaming or removing one raises it.
 *
 * @param schemaVersion 1 for this layout
 */
public record DataExportResponse(
        int schemaVersion,
        Instant exportedAt,
        UserProfile profile,
        List<ConnectionSummary> connections,
        BankingExport banking,
        InvestmentExport investments,
        PlanningExport planning) {

    public static final int SCHEMA_VERSION = 1;
}
