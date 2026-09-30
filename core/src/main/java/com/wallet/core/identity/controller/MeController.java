package com.wallet.core.identity.controller;

import com.wallet.core.identity.dto.UserResponse;
import com.wallet.core.identity.service.UserService;
import com.wallet.core.shared.security.CurrentUserId;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/internal")
@RequiredArgsConstructor
public class MeController {

    private final UserService userService;

    @GetMapping("/me")
    public UserResponse me(@CurrentUserId UUID userId) {
        return userService.findById(userId);
    }
}
