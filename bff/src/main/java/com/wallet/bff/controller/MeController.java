package com.wallet.bff.controller;

import com.wallet.bff.auth.CurrentUserId;
import com.wallet.bff.client.CoreApi;
import com.wallet.bff.model.dto.response.UserResponse;
import com.wallet.bff.service.AuthService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
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

    /** Deletes the account and everything in it (LGPD, right to erasure), and ends the session. */
    @DeleteMapping("/api/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@CurrentUserId UUID userId, HttpServletResponse response) {
        authService.deleteAccount(userId, response);
    }
}
