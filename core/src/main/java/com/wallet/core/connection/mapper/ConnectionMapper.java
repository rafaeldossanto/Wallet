package com.wallet.core.connection.mapper;

import com.wallet.core.connection.ConnectionStatus;
import com.wallet.core.connection.LinkedConnection;
import com.wallet.core.connection.dto.ConnectionResponse;
import com.wallet.core.connection.entity.Connection;
import com.wallet.core.provider.ProviderItem;
import lombok.experimental.UtilityClass;

import java.time.Instant;
import java.util.UUID;

@UtilityClass
public class ConnectionMapper {

    public static final String PROVIDER_PLUGGY = "PLUGGY";

    /** {@code providerUpdatedAt} starts empty so the first sync always runs. */
    public Connection toEntity(UUID userId, ProviderItem item, Instant now) {
        return Connection.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .provider(PROVIDER_PLUGGY)
                .providerItemId(item.id())
                .institutionName(item.institutionName())
                .institutionImageUrl(item.institutionImageUrl())
                .status(ConnectionStatus.ACTIVE)
                .consentExpiresAt(item.consentExpiresAt())
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    public ConnectionResponse toResponse(Connection connection) {
        return new ConnectionResponse(
                connection.getId(),
                connection.getInstitutionName(),
                connection.getInstitutionImageUrl(),
                connection.getStatus(),
                connection.getLastSyncedAt(),
                connection.getConsentExpiresAt(),
                connection.getCreatedAt());
    }

    public LinkedConnection toLinked(Connection connection) {
        return new LinkedConnection(
                connection.getId(),
                connection.getUserId(),
                connection.getProviderItemId(),
                connection.getStatus(),
                connection.getProviderUpdatedAt(),
                connection.getLastSyncedAt());
    }
}
