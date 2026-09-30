package com.wallet.core.identity.service;

import java.time.Duration;

public record IssuedAccessToken(String value, Duration ttl) {
}
