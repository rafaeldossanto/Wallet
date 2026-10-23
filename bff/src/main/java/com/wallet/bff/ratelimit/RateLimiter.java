package com.wallet.bff.ratelimit;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Counters in memory, one fixed window per key. The Wallet runs a single BFF; with more than one
 * instance this moves to Redis like the Trilha BFF, behind the same {@link #tryAcquire}.
 *
 * <p>The window starts at the first hit and does not slide: later hits never extend its life.
 * Each key keeps the length it was created with, so a route can have a window of its own.
 */
@Component
public class RateLimiter {

    private final Cache<String, Window> windows = Caffeine.newBuilder()
            .maximumSize(100_000)
            .expireAfter(new Expiry<String, Window>() {
                @Override
                public long expireAfterCreate(String key, Window value, long currentTime) {
                    return value.nanos();
                }

                @Override
                public long expireAfterUpdate(String key, Window value, long currentTime, long currentDuration) {
                    return currentDuration;
                }

                @Override
                public long expireAfterRead(String key, Window value, long currentTime, long currentDuration) {
                    return currentDuration;
                }
            })
            .build();

    public boolean tryAcquire(String key, int limit, Duration window) {
        return windows.get(key, ignored -> new Window(new AtomicInteger(), window.toNanos())).hits().incrementAndGet() <= limit;
    }

    private record Window(AtomicInteger hits, long nanos) {
    }
}
