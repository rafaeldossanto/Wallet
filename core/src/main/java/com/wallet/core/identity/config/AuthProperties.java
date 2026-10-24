package com.wallet.core.identity.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * @param refreshReuseTolerance  how long after a rotation the previous refresh token is still
 *                               accepted, for the device whose response was lost (reload, Wi-Fi drop)
 * @param unconfirmedAccountTtl  an account whose e-mail was never confirmed is deleted after this
 * @param noticeInterval         at most one notice of each kind (sign-up attempt, lockout) per
 *                               account in this interval, so nobody can flood an inbox with them
 * @param cleanupCron            when unconfirmed accounts, old links and old failures are deleted,
 *                               in São Paulo time
 */
@ConfigurationProperties("wallet.auth")
public record AuthProperties(
        @DefaultValue("1m") Duration refreshReuseTolerance,
        @DefaultValue Lockout lockout,
        @DefaultValue PasswordChecks passwordChecks,
        @DefaultValue EmailLinks emailLinks,
        @DefaultValue("7d") Duration unconfirmedAccountTtl,
        @DefaultValue("1h") Duration noticeInterval,
        @DefaultValue("0 15 4 * * *") String cleanupCron) {

    /**
     * Failed sign-ins lock in two scopes (see {@code LoginThrottle}): one e-mail from one client
     * address, and one e-mail from anywhere.
     *
     * @param sourceMaxFailures  failures of one e-mail from one address before that address is locked out of it
     * @param accountMaxFailures failures of one e-mail from every address together before it locks for all
     * @param firstDuration      the first lockout; each one after it, while failures keep coming, lasts twice as long
     * @param maxDuration        no lockout lasts longer than this
     * @param forgetAfter        a day without failures and the counters start over
     */
    public record Lockout(
            @DefaultValue("5") int sourceMaxFailures,
            @DefaultValue("20") int accountMaxFailures,
            @DefaultValue("15m") Duration firstDuration,
            @DefaultValue("24h") Duration maxDuration,
            @DefaultValue("24h") Duration forgetAfter) {
    }

    /**
     * Every password check is a BCrypt, which costs a core's worth of CPU for tens of milliseconds.
     *
     * @param concurrency how many may run at once; 0 = half the processors (at least one)
     * @param maxWait     how long a check waits for its turn before the answer is {@code auth.busy}
     */
    public record PasswordChecks(
            @DefaultValue("0") int concurrency,
            @DefaultValue("2s") Duration maxWait) {

        public int resolvedConcurrency() {
            return concurrency > 0 ? concurrency : Math.max(1, Runtime.getRuntime().availableProcessors() / 2);
        }
    }

    /**
     * @param baseUrl            where the links in the e-mails point: the BFF's public address, which
     *                           serves the confirmation page ({@code WALLET_PUBLIC_URL}). Empty =
     *                           http://localhost:8080, development only; required with {@code prod}
     * @param ttl                how long the sign-up and e-mail change links work
     * @param maxPerHour         links of one kind sent to one account per hour (the sign-up's included)
     * @param passwordResetTtl   how long the "Esqueci minha senha" link works; shorter than {@code ttl}: it opens an account
     */
    public record EmailLinks(
            String baseUrl,
            @DefaultValue("24h") Duration ttl,
            @DefaultValue("3") int maxPerHour,
            @DefaultValue("1h") Duration passwordResetTtl) {
    }
}
