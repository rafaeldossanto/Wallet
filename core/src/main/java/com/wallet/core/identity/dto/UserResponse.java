package com.wallet.core.identity.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * @param pendingEmail     the address the account is moving to, until its link is opened (or expires)
 * @param twoFactorEnabled whether signing in asks for an authenticator code
 * @param termsAcceptanceRequired the user has not accepted the current version of the Terms of Use and the
 *                         Privacy Policy (never, or an older text): the app asks before going on (L15)
 * @param termsVersion     the current version, which {@code POST /me/terms} takes back
 * @param termsUrl         where the Terms of Use are read
 * @param privacyUrl       where the Privacy Policy is read
 */
public record UserResponse(UUID id, String email, String displayName, Instant createdAt,
                           String pendingEmail, boolean twoFactorEnabled, boolean termsAcceptanceRequired,
                           String termsVersion, String termsUrl, String privacyUrl) {
}
