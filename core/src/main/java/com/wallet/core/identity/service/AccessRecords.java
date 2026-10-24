package com.wallet.core.identity.service;

import com.wallet.core.identity.entity.AccessEvent;
import com.wallet.core.identity.mapper.AccessRecordMapper;
import com.wallet.core.identity.repository.AccessRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.UUID;

/**
 * Writes the records of access the Marco Civil da Internet asks for (art. 15): one per successful
 * login and per refresh, with the address the BFF saw. Never read back by the application.
 */
@Service
@RequiredArgsConstructor
class AccessRecords {

    private final AccessRecordRepository repository;
    private final Clock clock;

    /** @param clientAddress where the BFF saw the request come from; {@code unknown} without the header */
    void record(UUID userId, AccessEvent event, String clientAddress) {
        repository.save(AccessRecordMapper.toEntity(userId, event, clientAddress, clock.instant()));
    }
}
