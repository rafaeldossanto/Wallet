package com.wallet.core.identity.entity;

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

/**
 * A one-time link sent by e-mail. Only the SHA-256 of the token is kept: whoever reads the
 * database cannot open the links, the same as with refresh tokens.
 */
@Entity
@Table(name = "email_tokens")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmailToken {

    private @Id UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EmailTokenPurpose purpose;

    @Column(name = "token_hash", nullable = false)
    private String tokenHash;

    /** Only for {@link EmailTokenPurpose#CHANGE_EMAIL}: the address the account moves to. */
    @Column(name = "new_email")
    private String newEmail;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    /** Opened, or replaced by a newer link of the same purpose. */
    @Column(name = "used_at")
    private Instant usedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
