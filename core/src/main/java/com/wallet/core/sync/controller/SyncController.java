package com.wallet.core.sync.controller;

import com.wallet.core.shared.security.CurrentUserId;
import com.wallet.core.sync.SyncRunStatus;
import com.wallet.core.sync.dto.SyncStartedResponse;
import com.wallet.core.sync.service.ManualSyncService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class SyncController {

    private final ManualSyncService manualSyncService;

    @PostMapping("/internal/connections/{connectionId}/sync")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public SyncStartedResponse sync(@CurrentUserId UUID userId, @PathVariable UUID connectionId) {
        return new SyncStartedResponse(manualSyncService.request(userId, connectionId), SyncRunStatus.RUNNING);
    }
}
