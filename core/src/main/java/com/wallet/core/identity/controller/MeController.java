package com.wallet.core.identity.controller;

import com.wallet.core.identity.dto.AccountDeletionRequest;
import com.wallet.core.identity.dto.EmailChangeRequest;
import com.wallet.core.identity.dto.ProfileRequest;
import com.wallet.core.identity.dto.UserResponse;
import com.wallet.core.identity.service.AccountDeletionService;
import com.wallet.core.identity.service.UserService;
import com.wallet.core.shared.security.CurrentUserId;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/internal")
@RequiredArgsConstructor
public class MeController {

    private final UserService userService;
    private final AccountDeletionService accountDeletionService;

    @GetMapping("/me")
    public UserResponse me(@CurrentUserId UUID userId) {
        return userService.findById(userId);
    }

    @PutMapping("/me")
    public UserResponse update(@CurrentUserId UUID userId, @Valid @RequestBody ProfileRequest request) {
        return userService.rename(userId, request.displayName());
    }

    /**
     * Sends a link to the new e-mail; the account moves when it is opened. 202 whether or not the
     * new e-mail already has an account.
     */
    @PostMapping("/me/email")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void changeEmail(@CurrentUserId UUID userId,
                            @RequestHeader(name = ClientAddress.HEADER, required = false) String clientAddress,
                            @Valid @RequestBody EmailChangeRequest request) {
        userService.requestEmailChange(userId, request.newEmail(), request.password(), ClientAddress.of(clientAddress));
    }

    /**
     * The account of the token's user and everything in it, once its password matches. 204 also
     * when it was already gone.
     */
    @DeleteMapping("/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@CurrentUserId UUID userId,
                       @RequestHeader(name = ClientAddress.HEADER, required = false) String clientAddress,
                       @Valid @RequestBody AccountDeletionRequest request) {
        accountDeletionService.delete(userId, request.password(), ClientAddress.of(clientAddress));
    }
}
