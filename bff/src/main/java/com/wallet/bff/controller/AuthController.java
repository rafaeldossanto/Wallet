package com.wallet.bff.controller;

import com.wallet.bff.auth.ClientType;
import com.wallet.bff.auth.SecurityConfig;
import com.wallet.bff.client.CoreApi;
import com.wallet.bff.model.dto.request.EmailTokenRequest;
import com.wallet.bff.model.dto.request.ForgotPasswordRequest;
import com.wallet.bff.model.dto.request.PasswordResetRequest;
import com.wallet.bff.model.dto.request.LoginRequest;
import com.wallet.bff.model.dto.request.RefreshRequest;
import com.wallet.bff.model.dto.request.RegisterRequest;
import com.wallet.bff.model.dto.request.ResendConfirmationRequest;
import com.wallet.bff.model.dto.response.SessionResponse;
import com.wallet.bff.model.dto.response.EmailConfirmationResponse;
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
    private final CoreApi coreApi;

    /** 202 and no body for every e-mail; whoever owns it learns the rest in the inbox. */
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void register(@RequestBody RegisterRequest request) {
        authService.register(request);
    }

    /** Called by the confirmation page ({@code /email/confirm}) with the token of the e-mailed link. */
    @PostMapping("/email/confirm")
    public EmailConfirmationResponse confirmEmail(@RequestBody EmailTokenRequest request) {
        return coreApi.confirmEmail(request);
    }

    /** 202 for every e-mail, like the sign-up. */
    @PostMapping("/email/resend")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void resendConfirmation(@RequestBody ResendConfirmationRequest request) {
        coreApi.resendConfirmation(request);
    }

    /** 202 for every e-mail, like the sign-up; the core mails the link only to a confirmed account. */
    @PostMapping("/password/forgot")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void forgotPassword(@RequestBody ForgotPasswordRequest request) {
        coreApi.forgotPassword(request);
    }

    /** Called by the reset page ({@code /password/reset}) with the token of the e-mailed link and the new password. */
    @PostMapping("/password/reset")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetPassword(@RequestBody PasswordResetRequest request) {
        coreApi.resetPassword(request);
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
