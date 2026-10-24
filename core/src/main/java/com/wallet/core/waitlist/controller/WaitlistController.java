package com.wallet.core.waitlist.controller;

import com.wallet.core.waitlist.dto.WaitlistRequest;
import com.wallet.core.waitlist.dto.WaitlistTokenRequest;
import com.wallet.core.waitlist.service.WaitlistService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** No JWT, like {@code /internal/auth}: the landing page's visitors have no account. The gateway key still applies. */
@RestController
@RequestMapping("/internal/waitlist")
@RequiredArgsConstructor
public class WaitlistController {

    private final WaitlistService waitlistService;

    /** 202 with no body for every e-mail: the answer never tells whether it was already on the list. */
    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void subscribe(@Valid @RequestBody WaitlistRequest request) {
        waitlistService.subscribe(request.email());
    }

    /** Opens the confirmation link. Opening it again is not an error. */
    @PostMapping("/confirm")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void confirm(@Valid @RequestBody WaitlistTokenRequest request) {
        waitlistService.confirm(request.token());
    }

    /** 204 for any token: nobody learns which ones exist. */
    @PostMapping("/unsubscribe")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unsubscribe(@Valid @RequestBody WaitlistTokenRequest request) {
        waitlistService.unsubscribe(request.token());
    }
}
