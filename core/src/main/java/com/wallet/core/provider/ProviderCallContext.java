package com.wallet.core.provider;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Who a call to the provider is made for, and why. The audit trail reads it, so each recorded call
 * says which user, which connection and which trigger caused it:
 * {@code new ProviderCallContext(userId, connectionId, itemId, reason).call(() -> provider.findItem(itemId))}.
 *
 * <p>Held in a {@link ScopedValue}: bound only while the given code runs, on the calling thread,
 * and gone afterwards even when the code throws. A call made outside any context is still recorded,
 * without user, connection or reason.
 *
 * @param connectionId null while linking, before the connection exists
 */
public record ProviderCallContext(UUID userId, UUID connectionId, String providerItemId, ProviderCallReason reason) {

    private static final ScopedValue<ProviderCallContext> CURRENT = ScopedValue.newInstance();

    /** The context the calling thread runs in, if any. */
    public static Optional<ProviderCallContext> current() {
        return CURRENT.isBound() ? Optional.of(CURRENT.get()) : Optional.empty();
    }

    public void run(Runnable task) {
        ScopedValue.where(CURRENT, this).run(task);
    }

    public <T> T call(Supplier<T> task) {
        return ScopedValue.where(CURRENT, this).call(task::get);
    }
}
