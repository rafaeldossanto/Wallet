package com.wallet.core.waitlist.service;

import com.wallet.core.shared.error.DomainException;
import com.wallet.core.shared.error.ErrorType;
import com.wallet.core.shared.security.OpaqueTokens;
import com.wallet.core.waitlist.config.WaitlistProperties;
import com.wallet.core.waitlist.entity.WaitlistEntry;
import com.wallet.core.waitlist.entity.WaitlistStatus;
import com.wallet.core.waitlist.mapper.WaitlistMapper;
import com.wallet.core.waitlist.repository.WaitlistRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.Locale;

/**
 * The landing page's waitlist, with double opt-in: nobody is on the list until they open the link
 * sent to the address. The answer to a sign-up is the same for every address ("we sent you a
 * link"); what happens behind it depends on the address, and only its owner finds out, in the inbox:
 *
 * <ul>
 *   <li>new: an entry waiting for confirmation and a link (good for 24 h);</li>
 *   <li>waiting for confirmation already: a new link, the older one stops working, at most one an
 *       hour (inside the hour, nothing);</li>
 *   <li>on the list: nothing.</li>
 * </ul>
 *
 * <p>The transaction is opened here with a {@link TransactionTemplate}, and the e-mails are queued by
 * the mail module for after its commit: nothing waits on the network inside it.
 */
@Slf4j
@Service
public class WaitlistService {

    /** The text of the consent the landing page shows; a new text gets a new version. */
    static final String CONSENT_VERSION = "waitlist-v1";

    private final WaitlistRepository repository;
    private final WaitlistMailer mailer;
    private final WaitlistProperties properties;
    private final Clock clock;
    private final TransactionTemplate transactions;

    public WaitlistService(WaitlistRepository repository, WaitlistMailer mailer, WaitlistProperties properties,
                           Clock clock, PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.mailer = mailer;
        this.properties = properties;
        this.clock = clock;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    public void subscribe(String rawEmail) {
        String email = rawEmail.strip().toLowerCase(Locale.ROOT);
        String rawToken = OpaqueTokens.generate();
        String tokenHash = OpaqueTokens.hash(rawToken);
        transactions.executeWithoutResult(status -> {
            Instant now = clock.instant();
            Instant expiresAt = now.plus(properties.confirmTtl());
            WaitlistEntry candidate = WaitlistMapper.toPending(email, CONSENT_VERSION, tokenHash, now, expiresAt);
            if (repository.insertPendingIfAbsent(candidate.getId(), email, CONSENT_VERSION, now, tokenHash, expiresAt) == 1) {
                log.info("[WAITLIST] Entry {} signed up", candidate.getId());
                mailer.confirmation(email, rawToken);
            } else if (repository.renewPending(email, tokenHash, expiresAt, now, now.minus(properties.resendInterval())) == 1) {
                mailer.confirmation(email, rawToken);
            }
        });
    }

    /** Opening the link of someone already on the list is not an error, and sends nothing. */
    public void confirm(String rawToken) {
        String tokenHash = OpaqueTokens.hash(rawToken);
        String unsubscribeToken = OpaqueTokens.generate();
        transactions.executeWithoutResult(status -> {
            Instant now = clock.instant();
            WaitlistEntry entry = repository.findByConfirmTokenHash(tokenHash).orElseThrow(WaitlistService::invalidToken);
            if (WaitlistStatus.CONFIRMED.equals(entry.getStatus())) {
                return;
            }
            if (!entry.getConfirmTokenExpiresAt().isAfter(now)) {
                throw invalidToken();
            }
            if (repository.confirm(entry.getId(), now, unsubscribeToken) == 1) {
                log.info("[WAITLIST] Entry {} confirmed", entry.getId());
                mailer.welcome(entry.getEmail(), unsubscribeToken);
            }
        });
    }

    /** Deletes the entry the token belongs to. An unknown token is not an error: nobody learns which exist. */
    public void unsubscribe(String unsubscribeToken) {
        int deleted = transactions.execute(status -> repository.deleteByUnsubscribeToken(unsubscribeToken));
        if (deleted > 0) {
            log.info("[WAITLIST] {} entry left the list", deleted);
        }
    }

    private static DomainException invalidToken() {
        return new DomainException(ErrorType.VALIDATION, "waitlist.invalid_token",
                "The link is unknown, expired or replaced by a newer one");
    }
}
