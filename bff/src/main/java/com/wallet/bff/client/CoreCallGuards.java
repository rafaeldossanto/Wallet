package com.wallet.bff.client;

import com.wallet.bff.config.CoreProperties;
import com.wallet.bff.exception.CoreBusyException;
import com.wallet.bff.exception.CoreUnavailableException;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;

import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * One circuit breaker and one bulkhead per {@link CoreRouteGroup}. With a single circuit for
 * every route, one slow route (a Pluggy-bound link timing out, say) opened it and the whole app
 * answered 503; now it opens only for its group, and the rest keeps reaching the core.
 *
 * <p>The bulkhead is the outer one: a call refused for a full group never reaches the circuit, so
 * it neither counts as a success nor as a failure there, and a call the open circuit refuses gives
 * its slot back at once.
 */
@Component
public class CoreCallGuards {

    private final Map<CoreRouteGroup, CircuitBreaker> circuits = new EnumMap<>(CoreRouteGroup.class);
    private final Map<CoreRouteGroup, Bulkhead> bulkheads = new EnumMap<>(CoreRouteGroup.class);

    public CoreCallGuards(CoreProperties core) {
        for (CoreRouteGroup group : CoreRouteGroup.values()) {
            String name = "core-" + group.name().toLowerCase();
            circuits.put(group, CircuitBreaker.of(name, circuitConfig()));
            bulkheads.put(group, Bulkhead.of(name, BulkheadConfig.custom()
                    .maxConcurrentCalls(core.maxConcurrentCalls().of(group))
                    // Full answers at once: a wait here would only push the call past the app's patience.
                    .maxWaitDuration(Duration.ZERO)
                    .build()));
        }
    }

    /**
     * Opens when the core stops answering (I/O errors and timeouts only). Business errors such as
     * 401 or 409 are answers, not failures: they never open the circuit.
     */
    private static CircuitBreakerConfig circuitConfig() {
        return CircuitBreakerConfig.custom()
                .slidingWindowSize(20)
                .minimumNumberOfCalls(10)
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofSeconds(10))
                .permittedNumberOfCallsInHalfOpenState(3)
                .recordExceptions(ResourceAccessException.class)
                .build();
    }

    public <T> T call(CoreRouteGroup group, Supplier<T> request) {
        try {
            return bulkheads.get(group).executeSupplier(() -> circuits.get(group).executeSupplier(request));
        } catch (BulkheadFullException ex) {
            throw new CoreBusyException("Every call slot to the core for " + group + " is taken", ex);
        } catch (CallNotPermittedException ex) {
            throw new CoreUnavailableException("Circuit to the core for " + group + " is open", ex);
        } catch (ResourceAccessException ex) {
            throw new CoreUnavailableException("Core did not answer: " + ex.getMessage(), ex);
        }
    }

    public CircuitBreaker circuit(CoreRouteGroup group) {
        return circuits.get(group);
    }

    public Bulkhead bulkhead(CoreRouteGroup group) {
        return bulkheads.get(group);
    }
}
