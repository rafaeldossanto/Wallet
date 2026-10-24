package com.wallet.bff.controller;

import com.wallet.bff.client.CoreApi;
import com.wallet.bff.model.dto.request.WaitlistEmailRequest;
import com.wallet.bff.model.dto.request.WaitlistRequest;
import com.wallet.bff.model.dto.request.WaitlistTokenRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * The landing page's waitlist, public like {@code /api/auth}. Called from another origin (the
 * landing page) without credentials: its origin goes in {@code WALLET_WEB_ALLOWED_ORIGINS}.
 */
@RestController
@RequestMapping("/api/waitlist")
@RequiredArgsConstructor
public class WaitlistController {

    private final CoreApi coreApi;

    /** 202 and no body for every e-mail; whoever owns it learns the rest in the inbox. */
    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void subscribe(@Valid @RequestBody WaitlistRequest request) {
        coreApi.subscribeToWaitlist(new WaitlistEmailRequest(request.email()));
    }

    /** Called by the confirmation page ({@code /waitlist/confirm}) with the token of the e-mailed link. */
    @PostMapping("/confirm")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void confirm(@RequestBody WaitlistTokenRequest request) {
        coreApi.confirmWaitlist(request);
    }

    /** The token in the JSON body, as the page of the e-mailed link could send it. */
    @PostMapping(value = "/unsubscribe", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unsubscribe(@RequestBody WaitlistTokenRequest request) {
        coreApi.unsubscribeFromWaitlist(request);
    }

    /**
     * The token in the query: the unsubscribe page posts it that way, and so does the mail
     * provider's one-click button (RFC 8058: a form with {@code List-Unsubscribe=One-Click} to the
     * exact URL of the e-mail, whose body says nothing about whom).
     */
    @PostMapping("/unsubscribe")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unsubscribe(@RequestParam(required = false) String token) {
        coreApi.unsubscribeFromWaitlist(new WaitlistTokenRequest(token));
    }
}
