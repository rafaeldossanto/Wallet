package com.wallet.bff.service;

import com.wallet.bff.cache.ScreenCache;
import com.wallet.bff.client.CoreApi;
import com.wallet.bff.model.dto.request.LinkConnectionRequest;
import com.wallet.bff.model.dto.response.ConnectionResponse;
import com.wallet.bff.model.dto.response.SyncStartedResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/** Anything that changes a user's data drops that user's cached screens. */
@Service
@RequiredArgsConstructor
public class ConnectionService {

    private final CoreApi coreApi;
    private final ScreenCache cache;

    public List<ConnectionResponse> list() {
        return coreApi.connections();
    }

    public ConnectionResponse link(UUID userId, LinkConnectionRequest request) {
        ConnectionResponse linked = coreApi.link(request);
        cache.evictUser(userId);
        return linked;
    }

    public void unlink(UUID userId, UUID connectionId) {
        coreApi.unlink(connectionId);
        cache.evictUser(userId);
    }

    /**
     * Evicts right away even though the sync runs in the background: the next screen then shows
     * "atualizando" from the core instead of a minute-old copy.
     */
    public SyncStartedResponse sync(UUID userId, UUID connectionId) {
        SyncStartedResponse started = coreApi.sync(connectionId);
        cache.evictUser(userId);
        return started;
    }
}
