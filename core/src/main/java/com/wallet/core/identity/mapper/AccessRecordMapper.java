package com.wallet.core.identity.mapper;

import com.wallet.core.identity.entity.AccessEvent;
import com.wallet.core.identity.entity.AccessRecord;
import lombok.experimental.UtilityClass;

import java.time.Instant;
import java.util.UUID;

@UtilityClass
public class AccessRecordMapper {

    public AccessRecord toEntity(UUID userId, AccessEvent event, String clientAddress, Instant occurredAt) {
        return AccessRecord.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .event(event)
                .ip(clientAddress)
                .occurredAt(occurredAt)
                .build();
    }
}
