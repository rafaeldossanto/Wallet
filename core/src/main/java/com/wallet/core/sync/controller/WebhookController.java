package com.wallet.core.sync.controller;

import com.wallet.core.sync.dto.PluggyWebhookRequest;
import com.wallet.core.sync.service.WebhookIntake;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Pluggy's webhook, relayed by the BFF (which checks its secret). No JWT: the provider has none.
 * The gateway key still applies. 202 at once, whatever the event: recording and handling it come
 * after, so the provider never waits on a sync.
 */
@RestController
@RequestMapping("/internal/webhooks")
@RequiredArgsConstructor
public class WebhookController {

    private final WebhookIntake intake;

    @PostMapping("/pluggy")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void pluggy(@Valid @RequestBody PluggyWebhookRequest request) {
        intake.receive(request);
    }
}
