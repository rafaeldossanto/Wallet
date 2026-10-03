package com.wallet.bff.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static java.util.Objects.nonNull;

/**
 * Short-lived cache of composed screens, always keyed by user: one user's home can never be
 * served to another. Anything that changes a user's data evicts all of that user's screens.
 */
@Component
public class ScreenCache {

    private final Cache<Key, Object> cache;
    private final Cache<UUID, String> syncStates;

    public ScreenCache(CacheProperties properties) {
        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(properties.screenTtl())
                .maximumSize(10_000)
                .build();
        this.syncStates = Caffeine.newBuilder()
                .expireAfterAccess(Duration.ofDays(1))
                .maximumSize(10_000)
                .build();
    }

    /**
     * Syncs finish in the core's background, where the BFF cannot see them. Every look at the
     * user's connections passes their state here (ids, last sync, status); when it differs from
     * the last look, a sync landed and the user's screens are stale.
     */
    public void noteSyncState(UUID userId, String state) {
        String previous = syncStates.asMap().put(userId, state);
        if (!state.equals(previous)) {
            evictUser(userId);
        }
    }

    public <T> T get(UUID userId, String screen, String variant, Supplier<T> loader) {
        return get(userId, screen, variant, loader, value -> true);
    }

    /** @param cacheable a partial answer (some part of the screen failed) must not be reused */
    @SuppressWarnings("unchecked")
    public <T> T get(UUID userId, String screen, String variant, Supplier<T> loader, Predicate<T> cacheable) {
        Key key = new Key(userId, screen, variant);
        Object cached = cache.getIfPresent(key);
        if (nonNull(cached)) {
            return (T) cached;
        }
        T value = loader.get();
        if (nonNull(value) && cacheable.test(value)) {
            cache.put(key, value);
        }
        return value;
    }

    public void evictUser(UUID userId) {
        cache.asMap().keySet().removeIf(key -> key.userId().equals(userId));
    }

    private record Key(UUID userId, String screen, String variant) {
    }
}
