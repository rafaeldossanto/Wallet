package com.wallet.core.identity.controller;

import com.wallet.core.identity.dto.NewRecoveryCodesRequest;
import com.wallet.core.identity.dto.RecoveryCodesResponse;
import com.wallet.core.identity.dto.TwoFactorCodeRequest;
import com.wallet.core.identity.dto.TwoFactorDisableRequest;
import com.wallet.core.identity.dto.TwoFactorSetupRequest;
import com.wallet.core.identity.dto.TwoFactorSetupResponse;
import com.wallet.core.identity.service.TwoFactorService;
import com.wallet.core.shared.security.CurrentUserId;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** The second factor (TOTP): set up with the password, turn on with the first code, turn off with password and code. */
@RestController
@RequestMapping("/internal/me/two-factor")
@RequiredArgsConstructor
public class TwoFactorController {

    private final TwoFactorService twoFactorService;

    @PostMapping("/setup")
    public TwoFactorSetupResponse setUp(@CurrentUserId UUID userId,
                                        @RequestHeader(name = ClientAddress.HEADER, required = false) String clientAddress,
                                        @Valid @RequestBody TwoFactorSetupRequest request) {
        return twoFactorService.setUp(userId, request.password(), ClientAddress.of(clientAddress));
    }

    @PostMapping("/enable")
    public RecoveryCodesResponse enable(@CurrentUserId UUID userId, @Valid @RequestBody TwoFactorCodeRequest request) {
        return twoFactorService.enable(userId, request.code());
    }

    /** Ten new recovery codes for the old ones, with the password and a code. */
    @PostMapping("/recovery-codes")
    public RecoveryCodesResponse renewRecoveryCodes(@CurrentUserId UUID userId,
                                                    @RequestHeader(name = ClientAddress.HEADER, required = false) String clientAddress,
                                                    @Valid @RequestBody NewRecoveryCodesRequest request) {
        return twoFactorService.renewRecoveryCodes(userId, request.password(), request.code(), ClientAddress.of(clientAddress));
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void disable(@CurrentUserId UUID userId,
                        @RequestHeader(name = ClientAddress.HEADER, required = false) String clientAddress,
                        @Valid @RequestBody TwoFactorDisableRequest request) {
        twoFactorService.disable(userId, request.password(), request.code(), ClientAddress.of(clientAddress));
    }
}
