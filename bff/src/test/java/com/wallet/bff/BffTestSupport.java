package com.wallet.bff;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.wallet.bff.config.GatewayKey;
import com.wallet.bff.trace.TraceIdFilter;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

/**
 * A fake core (WireMock) with a real signing key: it serves the JWKS, and tests sign tokens with
 * the private half, so the BFF validates them exactly as in production.
 *
 * <p>One server for every test class: same URL, so Spring reuses the cached contexts.
 */
public abstract class BffTestSupport {

    protected static final WireMockServer CORE = new WireMockServer(wireMockConfig().dynamicPort());
    protected static final RSAKey SIGNING_KEY = generateKey("wallet-core-1");

    /** What the fake core asks of the BFF; the JWKS stub refuses to answer without it. */
    protected static final String GATEWAY_KEY = "test-gateway-key-0123456789abcdef0123456789";

    static {
        CORE.start();
    }

    @DynamicPropertySource
    static void pointAtFakeCore(DynamicPropertyRegistry registry) {
        registry.add("wallet.core.url", CORE::baseUrl);
        registry.add("wallet.core.gateway-key", () -> GATEWAY_KEY);
    }

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private CircuitBreaker coreCircuitBreaker;

    @Autowired
    private TraceIdFilter traceIdFilter;

    protected MockMvc mockMvc;

    /**
     * {@code springSecurity()} only brings the security chain; the trace filter is added by hand,
     * ahead of it, the same order the server uses.
     */
    @BeforeEach
    void resetFakeCore() {
        CORE.resetAll();
        stubJwks(SIGNING_KEY);
        coreCircuitBreaker.reset();
        mockMvc = MockMvcBuilders.webAppContextSetup(context).addFilters(traceIdFilter).apply(springSecurity()).build();
    }

    /** The core's JWKS, served only to a caller with the gateway key, as the real core does. */
    protected static void stubJwks(RSAKey key) {
        CORE.stubFor(get(urlEqualTo("/internal/.well-known/jwks.json"))
                .withHeader(GatewayKey.HEADER, equalTo(GATEWAY_KEY))
                .willReturn(okJson(new JWKSet(key.toPublicJWK()).toString())));
    }

    protected static String token(UUID userId) {
        return token(userId, Instant.now().plus(Duration.ofMinutes(15)), "wallet-core", SIGNING_KEY);
    }

    protected static String token(UUID userId, Instant expiresAt, String issuer, RSAKey key) {
        try {
            SignedJWT jwt = new SignedJWT(
                    new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).build(),
                    new JWTClaimsSet.Builder()
                            .issuer(issuer)
                            .subject(userId.toString())
                            .issueTime(Date.from(Instant.now().minusSeconds(5)))
                            .expirationTime(Date.from(expiresAt))
                            .build());
            jwt.sign(new RSASSASigner(key));
            return jwt.serialize();
        } catch (JOSEException ex) {
            throw new IllegalStateException(ex);
        }
    }

    protected static String bearer(UUID userId) {
        return "Bearer " + token(userId);
    }

    protected static RSAKey generateKey(String keyId) {
        try {
            return new RSAKeyGenerator(2048).keyID(keyId).generate();
        } catch (JOSEException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
