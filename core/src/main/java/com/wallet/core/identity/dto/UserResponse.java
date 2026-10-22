package com.wallet.core.identity.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * @param pendingEmail     the address the account is moving to, until its link is opened (or expires)
 * @param twoFactorEnabled whether signing in asks for an authenticator code
 */
public record UserResponse(UUID id, String email, String displayName, Instant createdAt,
                           String pendingEmail, boolean twoFactorEnabled) {
}
