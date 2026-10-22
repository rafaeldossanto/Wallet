package com.wallet.core.shared.web;

import com.wallet.core.shared.error.ServiceBusyException;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/**
 * How many requests of one expensive kind run at the same time: in all, so together they never
 * take the whole database pool, and per user, so one user cannot take every slot and leave the
 * others with errors. With virtual threads nothing else bounds this: every request gets a thread.
 *
 * <p>A full bulkhead answers at once, it does not queue ({@link ServiceBusyException}): 429 when
 * the user already has their share running, 503 when all the users together took every slot.
 */
public final class Bulkhead {

    private final String kind;
    private final int maxPerUser;
    private final Semaphore slots;
    private final ConcurrentHashMap<UUID, Integer> runningPerUser = new ConcurrentHashMap<>();

    /** @throws IllegalArgumentException for limits that admit nothing or a user share above the total: the core does not start */
    public Bulkhead(String kind, BulkheadProperties.Limits limits) {
        if (limits.maxPerUser() < 1 || limits.maxConcurrent() < limits.maxPerUser()) {
            throw new IllegalArgumentException("wallet.bulkhead." + kind + ": max-per-user must be at least 1 "
                    + "and at most max-concurrent");
        }
        this.kind = kind;
        this.maxPerUser = limits.maxPerUser();
        this.slots = new Semaphore(limits.maxConcurrent());
    }

    /**
     * Takes a slot until the permit is closed. The user's share is checked first: a user over it
     * is refused without touching the slots the others share.
     *
     * @throws ServiceBusyException when the user already has their share running, or every slot is taken
     */
    public Permit enter(UUID userId) {
        if (runningPerUser.merge(userId, 1, Integer::sum) > maxPerUser) {
            leave(userId);
            throw ServiceBusyException.userShareTaken(kind);
        }
        if (!slots.tryAcquire()) {
            leave(userId);
            throw ServiceBusyException.full(kind);
        }
        return new Permit(userId);
    }

    public <T> T run(UUID userId, Supplier<T> work) {
        try (Permit ignored = enter(userId)) {
            return work.get();
        }
    }

    public int availableSlots() {
        return slots.availablePermits();
    }

    private void leave(UUID userId) {
        runningPerUser.computeIfPresent(userId, (user, running) -> running > 1 ? running - 1 : null);
    }

    /** Closing twice frees the slot once: the background sync closes it, and so does its error path. */
    public final class Permit implements AutoCloseable {

        private final UUID userId;
        private final AtomicBoolean closed = new AtomicBoolean();

        private Permit(UUID userId) {
            this.userId = userId;
        }

        @Override
        public void close() {
            if (closed.compareAndSet(false, true)) {
                slots.release();
                leave(userId);
            }
        }
    }
}
