package com.wallet.bff.controller;

import com.wallet.bff.client.CoreApi;
import com.wallet.bff.model.dto.request.NewRecoveryCodesRequest;
import com.wallet.bff.model.dto.request.TwoFactorCodeRequest;
import com.wallet.bff.model.dto.request.TwoFactorDisableRequest;
import com.wallet.bff.model.dto.request.TwoFactorSetupRequest;
import com.wallet.bff.model.dto.response.RecoveryCodesResponse;
import com.wallet.bff.model.dto.response.TwoFactorSetupResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * The second factor, relayed as it comes: the core checks the password and the codes. These
 * routes count per user against the sign-in limit ({@code RateLimitFilter}).
 */
@RestController
@RequestMapping("/api/me/two-factor")
@RequiredArgsConstructor
public class TwoFactorController {

    private final CoreApi coreApi;

    @PostMapping("/setup")
    public TwoFactorSetupResponse setUp(@RequestBody TwoFactorSetupRequest request) {
        return coreApi.setUpTwoFactor(request);
    }

    @PostMapping("/enable")
    public RecoveryCodesResponse enable(@RequestBody TwoFactorCodeRequest request) {
        return coreApi.enableTwoFactor(request);
    }

    /** Ten new recovery codes for the old ones; the core asks for the password and a code. */
    @PostMapping("/recovery-codes")
    public RecoveryCodesResponse newRecoveryCodes(@RequestBody NewRecoveryCodesRequest request) {
        return coreApi.newRecoveryCodes(request);
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void disable(@RequestBody TwoFactorDisableRequest request) {
        coreApi.disableTwoFactor(request);
    }
}
