package com.wallet.core.provider.service;

import com.wallet.core.provider.entity.ProviderCall;
import com.wallet.core.provider.entity.ProviderCallOutcome;
import com.wallet.core.provider.repository.ProviderCallRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.spi.LoggingEventBuilder;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Records each provider call twice: a {@code provider_calls} row, and one log line of
 * {@code key=value} pairs (also attached as SLF4J key-values, which Spring Boot's structured
 * logging turns into JSON fields) for a log platform.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProviderCallRecorder {

    private final ProviderCallRepository repository;

    /** Never throws: losing an audit record must not break the sync or the link that made the call. */
    public void record(ProviderCall call) {
        try {
            logLine(call);
            repository.insert(call);
        } catch (RuntimeException ex) {
            log.warn("[PROVIDER_CALL] Could not store the record of {} {}: {}",
                    call.provider(), call.operation(), ex.getMessage());
        }
    }

    /** Successes at INFO, failures at WARN. Empty fields are left out. */
    private static void logLine(ProviderCall call) {
        Map<String, Object> fields = fields(call);
        LoggingEventBuilder event = ProviderCallOutcome.SUCCESS.equals(call.outcome()) ? log.atInfo() : log.atWarn();
        fields.forEach(event::addKeyValue);
        event.log(fields.entrySet().stream()
                .map(field -> field.getKey() + "=" + field.getValue())
                .collect(Collectors.joining(" ", "[PROVIDER_CALL] ", "")));
    }

    private static Map<String, Object> fields(ProviderCall call) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("provider", call.provider());
        fields.put("operation", call.operation());
        fields.put("outcome", call.outcome());
        fields.put("status", call.httpStatus());
        fields.put("errorCode", call.errorCode());
        fields.put("durationMs", call.durationMs());
        fields.put("reason", call.reason());
        fields.put("userId", call.userId());
        fields.put("connectionId", call.connectionId());
        fields.put("itemId", call.providerItemId());
        fields.values().removeIf(Objects::isNull);
        return fields;
    }
}
