package com.wallet.core.shared.web;

import lombok.experimental.UtilityClass;

import java.net.URI;
import java.util.Locale;

import static org.springframework.util.StringUtils.hasText;

/** The BFF's public address ({@code WALLET_PUBLIC_URL}), where the links in the e-mails point. */
@UtilityClass
public class PublicUrl {

    private static final String DEVELOPMENT_BASE_URL = "http://localhost:8080";

    /**
     * Required with the {@code prod} profile, and https there: a link to localhost, or one sent in the
     * clear, would leave every new account unconfirmed or hand the token to the network.
     */
    public String resolve(String configured, boolean production) {
        if (!hasText(configured)) {
            if (production) {
                throw new IllegalStateException("WALLET_PUBLIC_URL is required with the prod profile: the e-mails "
                        + "link to the BFF's public address (e.g. https://wallet.example.com)");
            }
            return DEVELOPMENT_BASE_URL;
        }
        String url = configured.strip().replaceAll("/+$", "");
        URI uri = URI.create(url);
        if (!hasText(uri.getHost()) || !hasText(uri.getScheme())) {
            throw new IllegalStateException("WALLET_PUBLIC_URL must be an absolute URL, e.g. https://wallet.example.com");
        }
        if (production && !"https".equals(uri.getScheme().toLowerCase(Locale.ROOT))) {
            throw new IllegalStateException("WALLET_PUBLIC_URL must be https with the prod profile: "
                    + "the e-mailed links carry one-time tokens");
        }
        return url;
    }
}
