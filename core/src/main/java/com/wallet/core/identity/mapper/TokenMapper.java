package com.wallet.core.identity.mapper;

import com.wallet.core.identity.dto.TokenResponse;
import com.wallet.core.identity.service.IssuedAccessToken;
import com.wallet.core.identity.service.IssuedRefreshToken;
import lombok.experimental.UtilityClass;

@UtilityClass
public class TokenMapper {

    private static final String BEARER = "Bearer";

    public TokenResponse toResponse(IssuedAccessToken accessToken, IssuedRefreshToken refreshToken) {
        return new TokenResponse(
                accessToken.value(),
                BEARER,
                accessToken.ttl().toSeconds(),
                refreshToken.value(),
                refreshToken.expiresAt());
    }
}
