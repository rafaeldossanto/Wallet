package com.wallet.core.connection.entity;

import com.wallet.core.connection.ConnectionStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "connections")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Connection {

    private @Id UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private String provider;

    @Column(name = "provider_item_id", nullable = false)
    private String providerItemId;

    @Column(name = "institution_name")
    private String institutionName;

    @Column(name = "institution_image_url")
    private String institutionImageUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ConnectionStatus status;

    /** Provider's "last collected from the bank" at our last sync. Unchanged = nothing new. */
    @Column(name = "provider_updated_at")
    private Instant providerUpdatedAt;

    @Column(name = "last_synced_at")
    private Instant lastSyncedAt;

    @Column(name = "consent_expires_at")
    private Instant consentExpiresAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
