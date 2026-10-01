package com.wallet.bff.controller;

import com.wallet.bff.auth.CurrentUserId;
import com.wallet.bff.model.dto.request.LinkConnectionRequest;
import com.wallet.bff.model.dto.response.ConnectionResponse;
import com.wallet.bff.model.dto.response.SyncStartedResponse;
import com.wallet.bff.service.ConnectionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/connections")
@RequiredArgsConstructor
public class ConnectionController {

    private final ConnectionService connectionService;

    @GetMapping
    public List<ConnectionResponse> list() {
        return connectionService.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ConnectionResponse link(@CurrentUserId UUID userId, @RequestBody LinkConnectionRequest request) {
        return connectionService.link(userId, request);
    }

    @DeleteMapping("/{connectionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unlink(@CurrentUserId UUID userId, @PathVariable UUID connectionId) {
        connectionService.unlink(userId, connectionId);
    }

    @PostMapping("/{connectionId}/sync")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public SyncStartedResponse sync(@CurrentUserId UUID userId, @PathVariable UUID connectionId) {
        return connectionService.sync(userId, connectionId);
    }
}
