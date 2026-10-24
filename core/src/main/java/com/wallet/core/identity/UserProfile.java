package com.wallet.core.identity;

import java.time.Instant;
import java.util.UUID;

/**
 * What other modules may know about a user: who they are, never how they sign in. No password hash,
 * no second-factor secret, no token.
 *
 * @param emailConfirmedAt {@code null} while the link sent at sign-up has not been opened
 */
public record UserProfile(
        UUID id,
        String email,
        String displayName,
        Instant createdAt,
        Instant emailConfirmedAt,
        boolean twoFactorEnabled) {
}
