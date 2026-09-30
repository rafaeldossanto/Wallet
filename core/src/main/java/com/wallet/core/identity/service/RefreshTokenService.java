package com.wallet.core.identity.service;

import com.wallet.core.identity.config.AuthProperties;
import com.wallet.core.identity.config.JwtProperties;
import com.wallet.core.identity.entity.RefreshToken;
import com.wallet.core.identity.mapper.RefreshTokenMapper;
import com.wallet.core.identity.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

/**
 * Refresh token rotation with reuse detection.
 *
 * <p>Each refresh exchanges the token for a new one and retires the old. Presenting a retired
 * token again means someone else holds a copy, so the whole login (family) is revoked. The one
 * exception is the device that lost the response: within the tolerance window, and as long as
 * the successor was never used, the old token gets a fresh successor instead.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final RefreshTokenRepository repository;
    private final JwtProperties jwtProperties;
    private final AuthProperties authProperties;
    private final Clock clock;

    @Transactional
    public IssuedRefreshToken issueForNewLogin(UUID userId) {
        return issue(UUID.randomUUID(), userId, UUID.randomUUID(), clock.instant());
    }

    @Transactional
    public RotationResult rotate(String rawToken) {
        Instant now = clock.instant();
        Optional<RefreshToken> found = repository.findByTokenHash(OpaqueTokens.hash(rawToken));
        if (found.isEmpty()) {
            return new RotationResult.Invalid();
        }
        RefreshToken current = found.get();
        if (nonNull(current.getRevokedAt()) || !current.getExpiresAt().isAfter(now)) {
            return new RotationResult.Invalid();
        }
        if (isNull(current.getRotatedAt())) {
            return rotateCurrent(current, now);
        }
        return retryWithinTolerance(current, now);
    }

    @Transactional
    public void revokeFamilyOf(String rawToken) {
        repository.findByTokenHash(OpaqueTokens.hash(rawToken))
                .ifPresent(token -> repository.revokeFamily(token.getFamilyId(), clock.instant()));
    }

    private RotationResult rotateCurrent(RefreshToken current, Instant now) {
        UUID successorId = UUID.randomUUID();
        if (repository.markRotated(current.getId(), successorId, now) == 0) {
            // Someone rotated it between our read and our write: same as presenting it twice.
            return reuseDetected(current, now);
        }
        IssuedRefreshToken successor = issue(successorId, current.getUserId(), current.getFamilyId(), now);
        return new RotationResult.Rotated(current.getUserId(), successor);
    }

    private RotationResult retryWithinTolerance(RefreshToken current, Instant now) {
        boolean withinTolerance = current.getRotatedAt().plus(authProperties.refreshReuseTolerance()).isAfter(now);
        Optional<RefreshToken> successor = Optional.ofNullable(current.getReplacedById()).flatMap(repository::findById);
        boolean successorUntouched = successor
                .map(token -> isNull(token.getRotatedAt()) && isNull(token.getRevokedAt()))
                .orElse(false);
        if (!withinTolerance || !successorUntouched) {
            return reuseDetected(current, now);
        }

        UUID newSuccessorId = UUID.randomUUID();
        if (repository.replaceSuccessor(current.getId(), current.getReplacedById(), newSuccessorId) == 0) {
            return reuseDetected(current, now);
        }
        repository.revoke(current.getReplacedById(), now);
        IssuedRefreshToken newSuccessor = issue(newSuccessorId, current.getUserId(), current.getFamilyId(), now);
        return new RotationResult.Rotated(current.getUserId(), newSuccessor);
    }

    private RotationResult reuseDetected(RefreshToken current, Instant now) {
        log.warn("[SECURITY] Refresh token reuse for user {}: revoking family {}",
                current.getUserId(), current.getFamilyId());
        repository.revokeFamily(current.getFamilyId(), now);
        return new RotationResult.Reused();
    }

    private IssuedRefreshToken issue(UUID id, UUID userId, UUID familyId, Instant now) {
        String rawToken = OpaqueTokens.generate();
        Instant expiresAt = now.plus(jwtProperties.refreshTokenTtl());
        repository.save(RefreshTokenMapper.toEntity(id, userId, familyId, OpaqueTokens.hash(rawToken), now, expiresAt));
        return new IssuedRefreshToken(rawToken, expiresAt);
    }
}
