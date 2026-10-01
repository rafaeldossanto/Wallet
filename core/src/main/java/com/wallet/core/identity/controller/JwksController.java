package com.wallet.core.identity.controller;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Public half of the signing key, in the standard JWKS format. The BFF validates tokens with it,
 * so a key rotation (or the ephemeral key of a dev restart) reaches the BFF without configuration.
 */
@RestController
@RequiredArgsConstructor
public class JwksController {

    private final RSAKey jwtSigningKey;

    @GetMapping("/internal/.well-known/jwks.json")
    public Map<String, Object> jwks() {
        return new JWKSet(jwtSigningKey.toPublicJWK()).toJSONObject();
    }
}
