package com.wallet.core.identity.mapper;

import com.wallet.core.identity.entity.EmailToken;
import com.wallet.core.identity.entity.EmailTokenPurpose;
import lombok.experimental.UtilityClass;

import java.time.Instant;
import java.util.UUID;

@UtilityClass
public class EmailTokenMapper {

    public EmailToken toEntity(UUID userId, EmailTokenPurpose purpose, String tokenHash, String newEmail,
                               Instant now, Instant expiresAt) {
        return EmailToken.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .purpose(purpose)
                .tokenHash(tokenHash)
                .newEmail(newEmail)
                .createdAt(now)
                .expiresAt(expiresAt)
                .build();
    }
}
