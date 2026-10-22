package com.wallet.core.identity.mapper;

import com.wallet.core.identity.entity.RecoveryCode;
import lombok.experimental.UtilityClass;

import java.time.Instant;
import java.util.UUID;

@UtilityClass
public class RecoveryCodeMapper {

    public RecoveryCode toEntity(UUID userId, String codeHash, Instant now) {
        return RecoveryCode.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .codeHash(codeHash)
                .createdAt(now)
                .build();
    }
}
