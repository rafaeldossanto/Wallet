package com.wallet.bff.controller;

import com.wallet.bff.auth.CurrentUserId;
import com.wallet.bff.client.CoreApi;
import com.wallet.bff.model.dto.request.AccountDeletionRequest;
import com.wallet.bff.model.dto.request.EmailChangeRequest;
import com.wallet.bff.model.dto.request.PasswordChangeRequest;
import com.wallet.bff.model.dto.request.ProfileRequest;
import com.wallet.bff.model.dto.response.UserResponse;
import com.wallet.bff.service.AuthService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class MeController {

    private final CoreApi coreApi;
    private final AuthService authService;

    @GetMapping("/api/me")
    public UserResponse me() {
        return coreApi.me();
    }

    /** The name; the e-mail has its own route, since it needs the password and the new inbox. */
    @PutMapping("/api/me")
    public UserResponse update(@RequestBody ProfileRequest request) {
        return coreApi.updateMe(request);
    }

    /** 202: the core sends a link to the new e-mail and the account moves when it is opened. */
    @PostMapping("/api/me/email")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void changeEmail(@RequestBody EmailChangeRequest request) {
        coreApi.changeEmail(request);
    }

    /**
     * 204: the core checks the current password (403 {@code auth.wrong_password}), changes it and
     * ends every session, this one included; the app signs in again. Counts per user against the
     * sign-in limit ({@code RateLimitFilter}).
     */
    @PutMapping("/api/me/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@RequestBody PasswordChangeRequest request) {
        coreApi.changePassword(request);
    }

    /**
     * Deletes the account and everything in it (LGPD, right to erasure), and ends the session. The
     * body carries the password again: the core refuses with 403 {@code auth.wrong_password}.
     */
    @DeleteMapping("/api/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@CurrentUserId UUID userId, @RequestBody AccountDeletionRequest request,
                       HttpServletResponse response) {
        authService.deleteAccount(userId, request, response);
    }
}
