package com.wallet.core.provider.entity;

import com.wallet.core.provider.ProviderCallReason;
import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

/**
 * One row of {@code provider_calls}: one HTTP call to the provider. Written with JDBC, not JPA, in
 * a transaction of its own (see {@code ProviderCallRepository}). Holds no response data on purpose.
 *
 * @param operation  the Wallet's name for the endpoint ({@code FIND_ITEM}, {@code LIST_TRANSACTIONS}...)
 * @param reason     null, like the user and connection, for a call made outside a context
 * @param httpStatus null when no response came back (timeout, connection refused)
 * @param errorCode  null on success
 * @param durationMs from sending the request to closing the response, body included
 */
@Builder
public record ProviderCall(
        UUID id,
        Instant calledAt,
        String provider,
        String operation,
        ProviderCallReason reason,
        UUID userId,
        UUID connectionId,
        String providerItemId,
        ProviderCallOutcome outcome,
        Integer httpStatus,
        String errorCode,
        int durationMs) {
}
