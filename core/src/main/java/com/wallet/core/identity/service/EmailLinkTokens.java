package com.wallet.core.identity.service;

import com.wallet.core.identity.config.AuthProperties;
import com.wallet.core.identity.entity.EmailToken;
import com.wallet.core.identity.entity.EmailTokenPurpose;
import com.wallet.core.identity.mapper.EmailTokenMapper;
import com.wallet.core.identity.repository.EmailTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * The one-time tokens behind the e-mailed links: 256 random bits each ({@link OpaqueTokens}), kept
 * as SHA-256, good for {@code wallet.auth.email-links.ttl} (24 h) and only the newest of a purpose.
 */
@Service
@RequiredArgsConstructor
public class EmailLinkTokens {

    private final EmailTokenRepository repository;
    private final AuthProperties properties;
    private final Clock clock;

    /** Whether one more link of this purpose may go to the account in this hour. */
    @Transactional(readOnly = true)
    public boolean mayIssue(UUID userId, EmailTokenPurpose purpose) {
        Instant hourAgo = clock.instant().minusSeconds(3600);
        return repository.countByUserIdAndPurposeAndCreatedAtAfter(userId, purpose, hourAgo)
                < properties.emailLinks().maxPerHour();
    }

    /** A new link; the earlier ones of the same purpose stop working. Returns the raw token, for the e-mail only. */
    @Transactional
    public String issue(UUID userId, EmailTokenPurpose purpose, String newEmail) {
        Instant now = clock.instant();
        repository.retireOpen(userId, purpose, now);
        String rawToken = OpaqueTokens.generate();
        repository.save(EmailTokenMapper.toEntity(userId, purpose, OpaqueTokens.hash(rawToken), newEmail, now,
                now.plus(properties.emailLinks().ttl())));
        return rawToken;
    }

    /** Uses the link up: unknown, expired, already opened or replaced = empty. Runs in the caller's transaction. */
    @Transactional
    public Optional<EmailToken> consume(String rawToken) {
        Instant now = clock.instant();
        return repository.findByTokenHash(OpaqueTokens.hash(rawToken))
                .filter(token -> token.getExpiresAt().isAfter(now))
                .filter(token -> repository.markUsed(token.getId(), now) == 1);
    }

    /** The address an account is moving to, while its link is open. */
    @Transactional(readOnly = true)
    public Optional<String> pendingEmail(UUID userId) {
        return repository.findFirstByUserIdAndPurposeAndUsedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(
                userId, EmailTokenPurpose.CHANGE_EMAIL, clock.instant()).map(EmailToken::getNewEmail);
    }
}
