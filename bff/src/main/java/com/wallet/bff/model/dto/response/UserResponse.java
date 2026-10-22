package com.wallet.bff.model.dto.response;

import java.time.Instant;
import java.util.UUID;

/**
 * @param pendingEmail     the address the account is moving to, until its link is opened
 * @param twoFactorEnabled whether signing in asks for an authenticator code; relayed as the core
 *                         sent it ({@code null} from a core older than the second factor)
 */
public record UserResponse(UUID id, String email, String displayName, Instant createdAt,
                           String pendingEmail, Boolean twoFactorEnabled) {
}
