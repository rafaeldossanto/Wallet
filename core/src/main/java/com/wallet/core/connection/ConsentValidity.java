package com.wallet.core.connection;

import lombok.experimental.UtilityClass;

import java.time.Instant;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

/** When an Open Finance authorization has ended, and when a renewal really renewed it. Pure: reads nothing. */
@UtilityClass
public class ConsentValidity {

    /**
     * The authorization ended: the connection is marked so, or its date has passed (the poll may not
     * have looked at it yet). A connection without a date never ends (Meu Pluggy items carry none).
     * On the very instant of the date it has ended.
     */
    public boolean ended(ConnectionStatus status, Instant expiresAt, Instant now) {
        return ConnectionStatus.CONSENT_EXPIRED.equals(status) || (nonNull(expiresAt) && !expiresAt.isAfter(now));
    }

    public boolean ended(LinkedConnection connection, Instant now) {
        return ended(connection.status(), connection.consentExpiresAt(), now);
    }

    /**
     * What the provider reports after the user went through the widget again: a date in the future
     * that moved past the one the Wallet had. The same date, or none, means the widget was closed
     * without renewing.
     */
    public boolean renewed(Instant previousExpiresAt, Instant reportedExpiresAt, Instant now) {
        return nonNull(reportedExpiresAt) && reportedExpiresAt.isAfter(now)
                && (isNull(previousExpiresAt) || reportedExpiresAt.isAfter(previousExpiresAt));
    }
}
