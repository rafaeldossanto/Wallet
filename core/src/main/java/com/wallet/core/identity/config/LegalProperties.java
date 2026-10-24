package com.wallet.core.identity.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import static org.springframework.util.StringUtils.hasText;

/**
 * The legal texts the user accepts (LGPD L15). The mechanism does not depend on what the texts say:
 * when they change, change {@code termsVersion} and everyone is asked to accept again.
 *
 * <p>A blank value (an environment variable set to nothing, as the {@code .env.example} has them) means
 * the default: an empty version would make every account look as if it had never accepted.
 *
 * @param termsVersion what the user accepted is recorded with this label ({@code users.terms_version},
 *                     at most 32 characters). Anything that differs from the user's means asking again
 * @param termsUrl     where the Terms of Use are read (the landing page's)
 * @param privacyUrl   where the Privacy Policy is read
 */
@ConfigurationProperties("wallet.legal")
public record LegalProperties(
        @DefaultValue(DEFAULT_VERSION) String termsVersion,
        @DefaultValue(DEFAULT_TERMS_URL) String termsUrl,
        @DefaultValue(DEFAULT_PRIVACY_URL) String privacyUrl) {

    static final String DEFAULT_VERSION = "2026-10-preliminar";
    static final String DEFAULT_TERMS_URL = "http://localhost:5181/termos.html";
    static final String DEFAULT_PRIVACY_URL = "http://localhost:5181/privacidade.html";

    public LegalProperties {
        termsVersion = hasText(termsVersion) ? termsVersion.strip() : DEFAULT_VERSION;
        termsUrl = hasText(termsUrl) ? termsUrl.strip() : DEFAULT_TERMS_URL;
        privacyUrl = hasText(privacyUrl) ? privacyUrl.strip() : DEFAULT_PRIVACY_URL;
    }
}
