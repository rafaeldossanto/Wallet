package com.wallet.core.provider.repository;

import com.wallet.core.provider.entity.ProviderCall;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;

import static java.util.Objects.isNull;

@Repository
@RequiredArgsConstructor
public class ProviderCallRepository {

    private final JdbcTemplate jdbc;

    /**
     * In a transaction of its own: linking calls the provider inside its transaction, and the record
     * must stay when the link rolls back. A failed insert, likewise, never touches the caller's.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void insert(ProviderCall call) {
        jdbc.update("""
                INSERT INTO provider_calls (id, called_at, provider, operation, reason, user_id, connection_id,
                                            provider_item_id, outcome, http_status, error_code, duration_ms)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, call.id(), Timestamp.from(call.calledAt()), call.provider(), call.operation(),
                isNull(call.reason()) ? null : call.reason().name(), call.userId(), call.connectionId(),
                call.providerItemId(), call.outcome().name(), call.httpStatus(), call.errorCode(), call.durationMs());
    }

    @Transactional
    public int deleteCalledBefore(Instant cutoff) {
        return jdbc.update("DELETE FROM provider_calls WHERE called_at < ?", Timestamp.from(cutoff));
    }
}
