package com.wallet.bff.controller;

import com.wallet.bff.client.CoreApi;
import com.wallet.bff.model.dto.response.UserResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class MeController {

    private final CoreApi coreApi;

    @GetMapping("/api/me")
    public UserResponse me() {
        return coreApi.me();
    }
}
