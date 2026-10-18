package com.wallet.bff.cache;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class ScreenCacheTest {

    private final ScreenCache cache = new ScreenCache(new CacheProperties(Duration.ofMinutes(1)));
    private final AtomicInteger loads = new AtomicInteger();

    @Test
    void reusesAScreenForTheSameUserOnly() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        assertThat(load(first)).isEqualTo("screen of " + first);
        assertThat(load(first)).isEqualTo("screen of " + first);
        assertThat(load(second)).isEqualTo("screen of " + second);

        assertThat(loads).hasValue(2);
    }

    @Test
    void evictingAUserLeavesOthersAlone() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        load(first);
        load(second);

        cache.evictUser(first);
        load(first);
        load(second);

        assertThat(loads).hasValue(3);
    }

    @Test
    void evictingAScreenDropsEveryVariantOfItAndNothingElse() {
        UUID user = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        for (String month : new String[] {"2026-10", "2026-11"}) {
            cache.get(user, "planning", month, () -> "plan " + loads.incrementAndGet());
        }
        cache.get(other, "planning", "2026-10", () -> "plan " + loads.incrementAndGet());
        load(user);

        cache.evictScreen(user, "planning");
        cache.get(user, "planning", "2026-10", () -> "plan " + loads.incrementAndGet());
        cache.get(user, "planning", "2026-11", () -> "plan " + loads.incrementAndGet());
        cache.get(other, "planning", "2026-10", () -> "plan " + loads.incrementAndGet());
        load(user);

        assertThat(loads).hasValue(6);
    }

    @Test
    void doesNotKeepAnswersMarkedAsNotCacheable() {
        UUID user = UUID.randomUUID();

        cache.get(user, "home", "", this::partial, value -> !value.startsWith("partial"));
        cache.get(user, "home", "", this::partial, value -> !value.startsWith("partial"));

        assertThat(loads).hasValue(2);
    }

    private String load(UUID user) {
        return cache.get(user, "home", "", () -> {
            loads.incrementAndGet();
            return "screen of " + user;
        });
    }

    private String partial() {
        loads.incrementAndGet();
        return "partial";
    }
}
