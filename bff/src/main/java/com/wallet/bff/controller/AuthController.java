package com.wallet.bff.controller;

import com.wallet.bff.auth.ClientType;
import com.wallet.bff.auth.SecurityConfig;
import com.wallet.bff.model.dto.request.LoginRequest;
import com.wallet.bff.model.dto.request.RefreshRequest;
import com.wallet.bff.model.dto.request.RegisterRequest;
import com.wallet.bff.model.dto.response.SessionResponse;
import com.wallet.bff.model.dto.response.UserResponse;
import com.wallet.bff.service.AuthService;
import com.wallet.bff.service.SessionDelivery;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Bodies are relayed as they come: the core validates them and answers with its own codes. */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse register(@RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @PostMapping("/login")
    public SessionResponse login(@RequestHeader(name = SecurityConfig.CLIENT_HEADER, required = false) String client,
                                 @RequestBody LoginRequest request, HttpServletResponse response) {
        return authService.login(ClientType.from(client), request, response);
    }

    @PostMapping("/refresh")
    public SessionResponse refresh(@RequestHeader(name = SecurityConfig.CLIENT_HEADER, required = false) String client,
                                   @RequestBody(required = false) RefreshRequest request,
                                   @CookieValue(name = SessionDelivery.REFRESH_COOKIE, required = false) String cookie,
                                   HttpServletResponse response) {
        return authService.refresh(ClientType.from(client), request, cookie, response);
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@RequestHeader(name = SecurityConfig.CLIENT_HEADER, required = false) String client,
                       @RequestBody(required = false) RefreshRequest request,
                       @CookieValue(name = SessionDelivery.REFRESH_COOKIE, required = false) String cookie,
                       HttpServletResponse response) {
        authService.logout(ClientType.from(client), request, cookie, response);
    }
}
