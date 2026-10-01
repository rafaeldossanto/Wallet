package com.wallet.bff.client;

import com.wallet.bff.model.dto.response.UserResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;

import java.util.Map;

/** The core's routes, typed. One method per route the BFF uses. */
@Component
@RequiredArgsConstructor
public class CoreApi {

    private final CoreClient client;

    public UserResponse me() {
        return client.get("/internal/me", Map.of(), new ParameterizedTypeReference<>() {});
    }
}
