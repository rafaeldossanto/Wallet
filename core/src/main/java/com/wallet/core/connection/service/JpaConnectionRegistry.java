package com.wallet.core.connection.service;

import com.wallet.core.connection.ConnectionRegistry;
import com.wallet.core.connection.ConnectionStatus;
import com.wallet.core.connection.LinkedConnection;
import com.wallet.core.connection.entity.Connection;
import com.wallet.core.connection.mapper.ConnectionMapper;
import com.wallet.core.connection.repository.ConnectionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import static java.util.Objects.nonNull;

@Service
@RequiredArgsConstructor
class JpaConnectionRegistry implements ConnectionRegistry {

    private final ConnectionRepository repository;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public Optional<LinkedConnection> find(UUID connectionId) {
        return repository.findById(connectionId).map(ConnectionMapper::toLinked);
    }

    @Override
    @Transactional(readOnly = true)
    public List<LinkedConnection> findSchedulable() {
        return repository.findByStatusIn(EnumSet.of(ConnectionStatus.ACTIVE, ConnectionStatus.SYNCING)).stream()
                .map(ConnectionMapper::toLinked)
                .toList();
    }

    @Override
    @Transactional
    public void markSyncing(UUID connectionId) {
        update(connectionId, connection -> connection.setStatus(ConnectionStatus.SYNCING));
    }

    @Override
    @Transactional
    public void recordSyncSucceeded(UUID connectionId, Instant providerUpdatedAt, Instant syncedAt,
                                    String institutionName, String institutionImageUrl, Instant consentExpiresAt) {
        update(connectionId, connection -> {
            connection.setStatus(ConnectionStatus.ACTIVE);
            connection.setProviderUpdatedAt(providerUpdatedAt);
            connection.setLastSyncedAt(syncedAt);
            if (nonNull(institutionName)) {
                connection.setInstitutionName(institutionName);
            }
            if (nonNull(institutionImageUrl)) {
                connection.setInstitutionImageUrl(institutionImageUrl);
            }
            connection.setConsentExpiresAt(consentExpiresAt);
        });
    }

    @Override
    @Transactional
    public void recordNeedsAttention(UUID connectionId) {
        update(connectionId, connection -> connection.setStatus(ConnectionStatus.NEEDS_ATTENTION));
    }

    @Override
    @Transactional
    public void recordSyncEnded(UUID connectionId) {
        update(connectionId, connection -> {
            if (ConnectionStatus.SYNCING.equals(connection.getStatus())) {
                connection.setStatus(ConnectionStatus.ACTIVE);
            }
        });
    }

    /** A connection unlinked while a sync ran is simply gone: nothing to record. */
    private void update(UUID connectionId, Consumer<Connection> change) {
        repository.findById(connectionId).ifPresent(connection -> {
            change.accept(connection);
            connection.setUpdatedAt(clock.instant());
        });
    }
}
