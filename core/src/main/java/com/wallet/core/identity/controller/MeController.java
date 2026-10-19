package com.wallet.core.identity.controller;

import com.wallet.core.identity.dto.AccountDeletionRequest;
import com.wallet.core.identity.dto.UserResponse;
import com.wallet.core.identity.service.AccountDeletionService;
import com.wallet.core.identity.service.UserService;
import com.wallet.core.shared.security.CurrentUserId;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
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

    /**
     * The account of the token's user and everything in it, once its password matches. 204 also
     * when it was already gone.
     */
    @DeleteMapping("/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@CurrentUserId UUID userId, @Valid @RequestBody AccountDeletionRequest request) {
        accountDeletionService.delete(userId, request.password());
    }
}
