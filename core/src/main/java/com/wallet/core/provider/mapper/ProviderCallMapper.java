package com.wallet.core.provider.mapper;

import com.wallet.core.provider.ProviderCallContext;
import com.wallet.core.provider.entity.ProviderCall;
import com.wallet.core.provider.entity.ProviderCallOutcome;
import lombok.experimental.UtilityClass;

import java.time.Instant;
import java.util.UUID;

import static java.util.Objects.isNull;

@UtilityClass
public class ProviderCallMapper {

    /**
     * @param context   null for a call made outside any context
     * @param errorCode null when the call succeeded, which is what decides the outcome
     */
    public ProviderCall toEntity(String provider, String operation, ProviderCallContext context, Instant calledAt,
                                 long durationMs, Integer httpStatus, String errorCode) {
        return ProviderCall.builder()
                .id(UUID.randomUUID())
                .calledAt(calledAt)
                .provider(provider)
                .operation(operation)
                .reason(isNull(context) ? null : context.reason())
                .userId(isNull(context) ? null : context.userId())
                .connectionId(isNull(context) ? null : context.connectionId())
                .providerItemId(isNull(context) ? null : context.providerItemId())
                .outcome(isNull(errorCode) ? ProviderCallOutcome.SUCCESS : ProviderCallOutcome.FAILURE)
                .httpStatus(httpStatus)
                .errorCode(errorCode)
                .durationMs((int) Math.min(durationMs, Integer.MAX_VALUE))
                .build();
    }
}
