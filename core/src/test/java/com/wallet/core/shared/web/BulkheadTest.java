package com.wallet.core.shared.web;

import com.wallet.core.shared.error.ErrorType;
import com.wallet.core.shared.error.ServiceBusyException;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BulkheadTest {

    private final Bulkhead bulkhead = new Bulkhead("search", new BulkheadProperties.Limits(3, 2));

    @Test
    void aUserPastTheirShareIsToldToSlowDownWithoutTakingASlot() {
        UUID heavy = UUID.randomUUID();
        bulkhead.enter(heavy);
        bulkhead.enter(heavy);

        assertThatThrownBy(() -> bulkhead.enter(heavy))
                .isInstanceOfSatisfying(ServiceBusyException.class, busy -> {
                    assertThat(busy.getType()).isEqualTo(ErrorType.TOO_MANY_REQUESTS);
                    assertThat(busy.getCode()).isEqualTo("rate_limit.exceeded");
                });
        assertThat(bulkhead.availableSlots()).isEqualTo(1);
    }

    /** The whole point: one user's load leaves room for somebody else. */
    @Test
    void anotherUserStillGetsInWhileOneHasTheirShareRunning() {
        UUID heavy = UUID.randomUUID();
        bulkhead.enter(heavy);
        bulkhead.enter(heavy);

        Bulkhead.Permit other = bulkhead.enter(UUID.randomUUID());

        assertThat(other).isNotNull();
        assertThat(bulkhead.availableSlots()).isZero();
    }

    @Test
    void everySlotTakenIsBusyForEveryone() {
        bulkhead.enter(UUID.randomUUID());
        bulkhead.enter(UUID.randomUUID());
        bulkhead.enter(UUID.randomUUID());
        UUID late = UUID.randomUUID();

        assertThatThrownBy(() -> bulkhead.enter(late))
                .isInstanceOfSatisfying(ServiceBusyException.class, busy -> {
                    assertThat(busy.getType()).isEqualTo(ErrorType.UNAVAILABLE);
                    assertThat(busy.getCode()).isEqualTo("service.busy");
                });
    }

    /** Refused for a full bulkhead, the user's attempt must not count against their own share later. */
    @Test
    void aRefusalLeavesNothingBehind() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        UUID third = UUID.randomUUID();
        Bulkhead.Permit a = bulkhead.enter(first);
        bulkhead.enter(second);
        bulkhead.enter(third);
        UUID refused = UUID.randomUUID();
        assertThatThrownBy(() -> bulkhead.enter(refused)).isInstanceOf(ServiceBusyException.class);
        assertThatThrownBy(() -> bulkhead.enter(refused)).isInstanceOf(ServiceBusyException.class);

        a.close();

        bulkhead.enter(refused);
    }

    @Test
    void closingFreesTheSlotAndTheShareOnce() {
        UUID user = UUID.randomUUID();
        Bulkhead.Permit permit = bulkhead.enter(user);
        bulkhead.enter(user);

        permit.close();
        permit.close();

        assertThat(bulkhead.availableSlots()).isEqualTo(2);
        bulkhead.enter(user);
        assertThatThrownBy(() -> bulkhead.enter(user)).isInstanceOf(ServiceBusyException.class);
    }

    @Test
    void runFreesTheSlotEvenWhenTheWorkFails() {
        UUID user = UUID.randomUUID();

        assertThatThrownBy(() -> bulkhead.run(user, () -> {
            throw new IllegalStateException("boom");
        })).hasMessage("boom");

        assertThat(bulkhead.availableSlots()).isEqualTo(3);
        assertThat(bulkhead.run(user, () -> "done")).isEqualTo("done");
    }

    /** Twenty requests of one user at once: never more than their share inside, and all slots back at the end. */
    @Test
    void concurrentRequestsOfOneUserNeverPassTheirShare() throws Exception {
        UUID user = UUID.randomUUID();
        AtomicInteger inside = new AtomicInteger();
        AtomicInteger mostInside = new AtomicInteger();
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService threads = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<?>[] calls = new Future<?>[20];
            for (int index = 0; index < calls.length; index++) {
                calls[index] = threads.submit(() -> {
                    start.await();
                    try {
                        return bulkhead.run(user, () -> {
                            mostInside.accumulateAndGet(inside.incrementAndGet(), Math::max);
                            sleep();
                            inside.decrementAndGet();
                            return true;
                        });
                    } catch (ServiceBusyException refused) {
                        return false;
                    }
                });
            }
            start.countDown();
            for (Future<?> call : calls) {
                call.get(10, TimeUnit.SECONDS);
            }
        }

        assertThat(mostInside.get()).isBetween(1, 2);
        assertThat(bulkhead.availableSlots()).isEqualTo(3);
    }

    @Test
    void limitsThatAdmitNothingStopTheBoot() {
        assertThatThrownBy(() -> new Bulkhead("search", new BulkheadProperties.Limits(2, 0)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("wallet.bulkhead.search");
        assertThatThrownBy(() -> new Bulkhead("search", new BulkheadProperties.Limits(1, 2)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static void sleep() {
        try {
            Thread.sleep(20);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}
