package com.wallet.core.identity.entity;

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

import static java.util.Objects.nonNull;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    private @Id UUID id;

    /** Always stored trimmed and lower case, so login is case-insensitive. */
    @Column(nullable = false)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    /** {@code null} until the link sent at sign-up is opened: nobody signs in before that. */
    @Column(name = "email_confirmed_at")
    private Instant emailConfirmedAt;

    /** The TOTP secret in base32, encrypted with the data key ({@code DataCipher}). */
    @Column(name = "totp_secret")
    private String totpSecret;

    /** {@code null} with a secret = set-up started, waiting for the first code. */
    @Column(name = "totp_enabled_at")
    private Instant totpEnabledAt;

    /** The last 30-second step accepted, so a code is good only once. */
    @Column(name = "totp_last_step")
    private Long totpLastStep;

    /**
     * When the password last changed (reset or change), empty if it never did. Access tokens issued
     * before it are refused ({@code ActiveUserInterceptor}): a JWT cannot be revoked.
     */
    @Column(name = "password_changed_at")
    private Instant passwordChangedAt;

    /**
     * The version of the Terms of Use and the Privacy Policy the user accepted, and when (L15).
     * {@code null} for an account from before the acceptance existed: asked at the next sign-in.
     */
    @Column(name = "terms_version")
    private String termsVersion;

    @Column(name = "terms_accepted_at")
    private Instant termsAcceptedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public boolean isEmailConfirmed() {
        return nonNull(emailConfirmedAt);
    }

    public boolean isTwoFactorEnabled() {
        return nonNull(totpEnabledAt);
    }
}
