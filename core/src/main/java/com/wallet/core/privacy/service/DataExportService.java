package com.wallet.core.privacy.service;

import com.wallet.core.banking.BankingQueries;
import com.wallet.core.connection.ConnectionRegistry;
import com.wallet.core.identity.UserProfiles;
import com.wallet.core.investment.InvestmentQueries;
import com.wallet.core.planning.PlanningExports;
import com.wallet.core.privacy.dto.DataExportResponse;
import com.wallet.core.shared.error.DomainException;
import com.wallet.core.shared.error.ErrorType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

/**
 * The export of a user's data: the other modules' read APIs, put in one file. One read-only
 * transaction, so the parts come from one connection and one moment.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DataExportService {

    private final UserProfiles profiles;
    private final ConnectionRegistry connections;
    private final BankingQueries banking;
    private final InvestmentQueries investments;
    private final PlanningExports planning;
    private final Clock clock;

    @Transactional(readOnly = true)
    public DataExportResponse export(UUID userId) {
        DataExportResponse export = new DataExportResponse(
                DataExportResponse.SCHEMA_VERSION,
                clock.instant(),
                profiles.find(userId).orElseThrow(DataExportService::unauthenticated),
                connections.summariesOf(userId),
                banking.export(userId),
                investments.export(userId),
                planning.export(userId));
        log.info("[PRIVACY] Data export of user {}: {} transactions, {} accounts", userId,
                export.banking().transactions().size(), export.banking().accounts().size());
        return export;
    }

    /** A valid token for a user that no longer exists: treat as logged out. */
    private static DomainException unauthenticated() {
        return new DomainException(ErrorType.UNAUTHORIZED, "auth.unauthenticated", "User of this token no longer exists");
    }
}
