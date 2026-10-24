package com.wallet.core.waitlist.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * @param baseUrl        where the links in the e-mails point: the BFF's public address, which serves the
 *                       waitlist pages ({@code WALLET_PUBLIC_URL}). Empty = http://localhost:8080,
 *                       development only; required with {@code prod}
 * @param confirmTtl     how long the confirmation link works
 * @param resendInterval a person who signs up again while unconfirmed gets a new link at most this often
 * @param pendingTtl     an entry nobody confirmed is deleted after this (LGPD: no address is kept for nothing)
 * @param cleanupCron    when those entries are deleted, in São Paulo time
 * @param announce       the opening announcement, a command run on the server ({@code WaitlistAnnouncementRunner})
 */
@ConfigurationProperties("wallet.waitlist")
public record WaitlistProperties(
        String baseUrl,
        @DefaultValue("24h") Duration confirmTtl,
        @DefaultValue("1h") Duration resendInterval,
        @DefaultValue("7d") Duration pendingTtl,
        @DefaultValue("0 45 4 * * *") String cleanupCron,
        @DefaultValue Announce announce) {

    /**
     * @param id            names the announcement; whoever already got this id is skipped, so running
     *                      the command again resends nothing. The command only runs when this and
     *                      {@code file} are set, and then the process exits when it is done
     * @param file          UTF-8 text: the first line is the subject, the rest is the body
     * @param ratePerSecond e-mails per second, so the SMTP provider's sending rate is respected
     */
    public record Announce(
            String id,
            String file,
            @DefaultValue("1") int ratePerSecond) {
    }
}
