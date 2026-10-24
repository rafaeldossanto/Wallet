package com.wallet.core.connection.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * What the user agreed to when they linked or renewed a connection (LGPD L3), and when they took it
 * back. Not tied to the connection by a foreign key: the record of the revocation outlives the
 * unlinked connection, and goes with the account.
 */
@Entity
@Table(name = "connection_consents")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConnectionConsent {

    private @Id UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "connection_id", nullable = false)
    private UUID connectionId;

    @Column(name = "purpose_version", nullable = false)
    private String purposeVersion;

    @Column(name = "data_scopes", nullable = false)
    private String dataScopes;

    @Column(name = "granted_at", nullable = false)
    private Instant grantedAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;
}
