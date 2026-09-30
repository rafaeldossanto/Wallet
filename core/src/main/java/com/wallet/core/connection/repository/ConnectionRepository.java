package com.wallet.core.connection.repository;

import com.wallet.core.connection.ConnectionStatus;
import com.wallet.core.connection.entity.Connection;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ConnectionRepository extends JpaRepository<Connection, UUID> {

    List<Connection> findByUserIdOrderByCreatedAtAsc(UUID userId);

    Optional<Connection> findByIdAndUserId(UUID id, UUID userId);

    boolean existsByProviderAndProviderItemId(String provider, String providerItemId);

    List<Connection> findByStatusIn(Collection<ConnectionStatus> statuses);
}
