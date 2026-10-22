package com.wallet.core.identity.config;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.proc.SecurityContext;
import com.wallet.core.shared.security.ProductionProfile;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.JwtAudienceValidator;
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
 *
 * <p>Without keys a development machine gets an ephemeral one; with the {@code prod} profile the
 * boot fails instead, since an ephemeral key logs everyone out on every restart.
 */
@Slf4j
@Configuration(proxyBeanMethods = false)
public class JwtKeyConfig {

    @Bean
    RSAKey jwtSigningKey(JwtProperties properties, Environment environment) {
        return resolve(properties, ProductionProfile.isActive(environment));
    }

    static RSAKey resolve(JwtProperties properties, boolean production) {
        boolean hasPrivate = hasText(properties.privateKey());
        boolean hasPublic = hasText(properties.publicKey());
        if (hasPrivate != hasPublic) {
            throw new IllegalStateException("Set both wallet.jwt.private-key and wallet.jwt.public-key, or neither");
        }
        if (!hasPrivate) {
            if (production) {
                throw new IllegalStateException("WALLET_JWT_PRIVATE_KEY and WALLET_JWT_PUBLIC_KEY are required with "
                        + "the prod profile: an ephemeral key would log everyone out on every restart");
            }
            log.warn("No JWT key configured: using an ephemeral RSA key. Sessions end on every restart. "
                    + "Set WALLET_JWT_PRIVATE_KEY and WALLET_JWT_PUBLIC_KEY to keep them.");
            return ephemeralKey();
        }
        return signingKey(PemKeys.readPublicKey(properties.publicKey()), PemKeys.readPrivateKey(properties.privateKey()));
    }

    /**
     * The key id is the key's own thumbprint (RFC 7638), so a new key always brings a new id. The
     * BFF caches the JWKS and only fetches it again when a token names a key id it has not seen:
     * a fixed id would leave it checking new tokens against the old key after a restart.
     */
    static RSAKey signingKey(RSAPublicKey publicKey, RSAPrivateKey privateKey) {
        try {
            return new RSAKey.Builder(publicKey)
                    .privateKey(privateKey)
                    .keyIDFromThumbprint()
                    .build();
        } catch (JOSEException ex) {
            throw new IllegalStateException("Could not compute the JWT key id", ex);
        }
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
        // A token without the audience (one issued before it existed) is refused: the app renews
        // it with its refresh token, which is opaque and unaffected.
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(timestampValidator,
                new JwtIssuerValidator(properties.issuer()), new JwtAudienceValidator(properties.audience())));
        return decoder;
    }

    private static RSAKey ephemeralKey() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair keyPair = generator.generateKeyPair();
            return signingKey((RSAPublicKey) keyPair.getPublic(), (RSAPrivateKey) keyPair.getPrivate());
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("RSA is not available in this JVM", ex);
        }
    }
}
