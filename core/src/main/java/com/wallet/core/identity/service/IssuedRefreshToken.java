package com.wallet.core.identity.service;

import java.time.Instant;

/** The raw token goes to the client exactly once; only its hash is kept. */
public record IssuedRefreshToken(String value, Instant expiresAt) {
}
