package com.wallet.bff.model.dto.response;

import java.time.Instant;
import java.util.UUID;

/**
 * @param pendingEmail     the address the account is moving to, until its link is opened
 * @param twoFactorEnabled whether signing in asks for an authenticator code; relayed as the core
 *                         sent it ({@code null} from a core older than the second factor)
 * @param termsAcceptanceRequired the user has not accepted the current Terms of Use and Privacy Policy (L15);
 *                         relayed as the core sent it ({@code null} from an older core)
 * @param termsVersion     the current version, which {@code POST /api/me/terms} takes back
 * @param termsUrl         where the Terms of Use are read
 * @param privacyUrl       where the Privacy Policy is read
 */
public record UserResponse(UUID id, String email, String displayName, Instant createdAt,
                           String pendingEmail, Boolean twoFactorEnabled, Boolean termsAcceptanceRequired,
                           String termsVersion, String termsUrl, String privacyUrl) {
}
