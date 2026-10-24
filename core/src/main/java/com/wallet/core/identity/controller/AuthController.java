package com.wallet.core.identity.controller;

import com.wallet.core.identity.dto.EmailConfirmationResponse;
import com.wallet.core.identity.dto.EmailTokenRequest;
import com.wallet.core.identity.dto.ForgotPasswordRequest;
import com.wallet.core.identity.dto.LoginRequest;
import com.wallet.core.identity.dto.PasswordResetRequest;
import com.wallet.core.identity.dto.RefreshTokenRequest;
import com.wallet.core.identity.dto.RegisterRequest;
import com.wallet.core.identity.dto.ResendConfirmationRequest;
import com.wallet.core.identity.dto.TokenResponse;
import com.wallet.core.identity.service.AuthService;
import com.wallet.core.identity.service.EmailConfirmationService;
import com.wallet.core.identity.service.PasswordService;
import com.wallet.core.identity.service.RegistrationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final RegistrationService registrationService;
    private final EmailConfirmationService emailConfirmationService;
    private final PasswordService passwordService;

    /** 202 with no body for every e-mail: the answer never tells whether the e-mail had an account. */
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void register(@Valid @RequestBody RegisterRequest request) {
        registrationService.register(request);
    }

    /** 202 with no body for every e-mail, like the sign-up: the link goes only to a confirmed account. */
    @PostMapping("/password/forgot")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        passwordService.forgot(request.email());
    }

    /** Opens the "Esqueci minha senha" link with the new password. */
    @PostMapping("/password/reset")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetPassword(@RequestHeader(name = ClientAddress.HEADER, required = false) String clientAddress,
                              @Valid @RequestBody PasswordResetRequest request) {
        passwordService.reset(request.token(), request.newPassword(), ClientAddress.of(clientAddress));
    }

    @PostMapping("/login")
    public TokenResponse login(@RequestHeader(name = ClientAddress.HEADER, required = false) String clientAddress,
                               @Valid @RequestBody LoginRequest request) {
        return authService.login(request, ClientAddress.of(clientAddress));
    }

    @PostMapping("/refresh")
    public TokenResponse refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return authService.refresh(request);
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@Valid @RequestBody RefreshTokenRequest request) {
        authService.logout(request);
    }

    /** Opens an e-mailed link: confirms the account, or moves it to the new e-mail. */
    @PostMapping("/email/confirm")
    public EmailConfirmationResponse confirmEmail(@Valid @RequestBody EmailTokenRequest request) {
        return emailConfirmationService.confirm(request.token());
    }

    /** Same 202 for every e-mail, like the sign-up. */
    @PostMapping("/email/resend")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void resendConfirmation(@Valid @RequestBody ResendConfirmationRequest request) {
        registrationService.resendConfirmation(request.email());
    }
}
