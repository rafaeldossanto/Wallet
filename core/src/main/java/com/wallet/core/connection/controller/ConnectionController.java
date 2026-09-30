package com.wallet.core.connection.controller;

import com.wallet.core.connection.dto.ConnectionResponse;
import com.wallet.core.connection.dto.LinkConnectionRequest;
import com.wallet.core.connection.service.ConnectionService;
import com.wallet.core.shared.security.CurrentUserId;
import jakarta.validation.Valid;
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
@RequestMapping("/internal/connections")
@RequiredArgsConstructor
public class ConnectionController {

    private final ConnectionService connectionService;

    @GetMapping
    public List<ConnectionResponse> list(@CurrentUserId UUID userId) {
        return connectionService.list(userId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ConnectionResponse link(@CurrentUserId UUID userId, @Valid @RequestBody LinkConnectionRequest request) {
        return connectionService.link(userId, request);
    }

    @DeleteMapping("/{connectionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unlink(@CurrentUserId UUID userId, @PathVariable UUID connectionId) {
        connectionService.unlink(userId, connectionId);
    }
}
