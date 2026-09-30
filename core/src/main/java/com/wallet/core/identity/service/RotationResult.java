package com.wallet.core.identity.service;

import java.util.UUID;

/**
 * Outcome of a refresh. Returned instead of thrown so that revoking a family on reuse is
 * committed: an exception inside the transaction would roll the revocation back.
 */
public sealed interface RotationResult {

    record Rotated(UUID userId, IssuedRefreshToken refreshToken) implements RotationResult {
    }

    /** An already rotated token came back outside the tolerance: likely stolen. Family revoked. */
    record Reused() implements RotationResult {
    }

    /** Unknown, expired or revoked token. */
    record Invalid() implements RotationResult {
    }
}
