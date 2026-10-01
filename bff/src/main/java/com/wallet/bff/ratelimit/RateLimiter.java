package com.wallet.bff.ratelimit;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Counters in memory, one fixed window per key. The Wallet runs a single BFF; with more than one
 * instance this moves to Redis like the Trilha BFF, behind the same {@link #tryAcquire}.
 *
 * <p>The window starts at the first hit and does not slide: later hits never extend its life.
 */
@Component
public class RateLimiter {

    private final Cache<String, AtomicInteger> windows;

    public RateLimiter(RateLimitProperties properties) {
        long windowNanos = properties.window().toNanos();
        this.windows = Caffeine.newBuilder()
                .maximumSize(100_000)
                .expireAfter(new Expiry<String, AtomicInteger>() {
                    @Override
                    public long expireAfterCreate(String key, AtomicInteger value, long currentTime) {
                        return windowNanos;
                    }

                    @Override
                    public long expireAfterUpdate(String key, AtomicInteger value, long currentTime, long currentDuration) {
                        return currentDuration;
                    }

                    @Override
                    public long expireAfterRead(String key, AtomicInteger value, long currentTime, long currentDuration) {
                        return currentDuration;
                    }
                })
                .build();
    }

    public boolean tryAcquire(String key, int limit) {
        return windows.get(key, ignored -> new AtomicInteger()).incrementAndGet() <= limit;
    }
}
