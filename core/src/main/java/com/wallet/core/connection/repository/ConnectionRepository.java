package com.wallet.core.connection.repository;

import com.wallet.core.connection.ConnectionStatus;
import com.wallet.core.connection.entity.Connection;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ConnectionRepository extends JpaRepository<Connection, UUID> {

    List<Connection> findByUserIdOrderByCreatedAtAsc(UUID userId);

    Optional<Connection> findByIdAndUserId(UUID id, UUID userId);

    boolean existsByProviderAndProviderItemId(String provider, String providerItemId);

    List<Connection> findByStatusIn(Collection<ConnectionStatus> statuses);

    /** Row lock: a concurrent unlink waits for the caller's transaction to end. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Connection> findLockedById(UUID id);
}
