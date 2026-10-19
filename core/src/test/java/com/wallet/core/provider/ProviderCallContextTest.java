package com.wallet.core.provider;

import com.wallet.core.shared.error.DomainException;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProviderCallContextTest {

    private final ProviderCallContext sync = new ProviderCallContext(
            UUID.randomUUID(), UUID.randomUUID(), "item-1", ProviderCallReason.MANUAL_SYNC);

    @Test
    void nothingIsBoundOutsideAContext() {
        assertThat(ProviderCallContext.current()).isEmpty();
    }

    @Test
    void boundWhileTheCodeRunsAndGoneAfterwards() {
        assertThat(sync.call(ProviderCallContext::current)).contains(sync);
        sync.run(() -> assertThat(ProviderCallContext.current()).contains(sync));

        assertThat(ProviderCallContext.current()).isEmpty();
    }

    @Test
    void goneEvenWhenTheCodeThrows() {
        assertThatThrownBy(() -> sync.run(() -> {
            throw ProviderErrors.unavailable("timeout");
        })).isInstanceOf(DomainException.class);

        assertThat(ProviderCallContext.current()).isEmpty();
    }

    @Test
    void anInnerContextWinsUntilItEnds() {
        ProviderCallContext unlink = new ProviderCallContext(
                sync.userId(), sync.connectionId(), sync.providerItemId(), ProviderCallReason.UNLINK);

        sync.run(() -> {
            assertThat(unlink.call(ProviderCallContext::current)).contains(unlink);
            assertThat(ProviderCallContext.current()).contains(sync);
        });
    }

    /** Work handed to another thread opens its own context, as the manual sync does on the executor. */
    @Test
    void otherThreadsDoNotInheritIt() {
        Optional<ProviderCallContext> seen = sync.call(
                () -> CompletableFuture.supplyAsync(ProviderCallContext::current).join());

        assertThat(seen).isEmpty();
    }
}
