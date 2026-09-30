package com.wallet.core.identity.config;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.proc.SecurityContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.time.Duration;

import static org.springframework.util.StringUtils.hasText;

/**
 * RS256 keys: the core signs with the private key, and anyone holding the public key (the BFF)
 * can validate but never mint a token.
 */
@Slf4j
@Configuration(proxyBeanMethods = false)
public class JwtKeyConfig {

    private static final String KEY_ID = "wallet-core-1";

    @Bean
    RSAKey jwtSigningKey(JwtProperties properties) {
        boolean hasPrivate = hasText(properties.privateKey());
        boolean hasPublic = hasText(properties.publicKey());
        if (hasPrivate != hasPublic) {
            throw new IllegalStateException("Set both wallet.jwt.private-key and wallet.jwt.public-key, or neither");
        }
        if (!hasPrivate) {
            log.warn("No JWT key configured: using an ephemeral RSA key. Tokens stop working on restart "
                    + "and the BFF cannot validate them. Set WALLET_JWT_PRIVATE_KEY and WALLET_JWT_PUBLIC_KEY.");
            return ephemeralKey();
        }
        return new RSAKey.Builder(PemKeys.readPublicKey(properties.publicKey()))
                .privateKey(PemKeys.readPrivateKey(properties.privateKey()))
                .keyID(KEY_ID)
                .build();
    }

    @Bean
    JwtEncoder jwtEncoder(RSAKey jwtSigningKey) {
        return new NimbusJwtEncoder(new ImmutableJWKSet<SecurityContext>(new JWKSet(jwtSigningKey)));
    }

    @Bean
    JwtDecoder jwtDecoder(RSAKey jwtSigningKey, JwtProperties properties, Clock clock) throws Exception {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(jwtSigningKey.toRSAPublicKey()).build();
        // Same clock that issues the tokens, so expiry can be tested by moving time.
        JwtTimestampValidator timestampValidator = new JwtTimestampValidator(Duration.ofSeconds(30));
        timestampValidator.setClock(clock);
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                timestampValidator, new JwtIssuerValidator(properties.issuer())));
        return decoder;
    }

    private static RSAKey ephemeralKey() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair keyPair = generator.generateKeyPair();
            return new RSAKey.Builder((RSAPublicKey) keyPair.getPublic())
                    .privateKey((RSAPrivateKey) keyPair.getPrivate())
                    .keyID(KEY_ID)
                    .build();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("RSA is not available in this JVM", ex);
        }
    }
}
