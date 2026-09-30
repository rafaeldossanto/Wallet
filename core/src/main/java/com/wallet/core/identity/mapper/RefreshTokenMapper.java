package com.wallet.core.identity.mapper;

import com.wallet.core.identity.entity.RefreshToken;
import lombok.experimental.UtilityClass;

import java.time.Instant;
import java.util.UUID;

@UtilityClass
public class RefreshTokenMapper {

    /**
     * The id is passed in (not generated here) because rotation needs it before the row exists:
     * the previous token is marked as replaced by this id in a compare-and-set.
     */
    public RefreshToken toEntity(UUID id, UUID userId, UUID familyId, String tokenHash,
                                 Instant now, Instant expiresAt) {
        return RefreshToken.builder()
                .id(id)
                .userId(userId)
                .familyId(familyId)
                .tokenHash(tokenHash)
                .createdAt(now)
                .expiresAt(expiresAt)
                .build();
    }
}
