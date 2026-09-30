package com.wallet.core.identity.service;

import com.wallet.core.identity.config.AuthProperties;
import com.wallet.core.identity.entity.User;
import com.wallet.core.identity.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import static java.util.Objects.nonNull;

/**
 * Separate bean on purpose: the failure has to be committed even though the login then throws
 * {@code auth.invalid_credentials}. Inside the login's own transaction it would roll back.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LoginAttemptService {

    private final UserRepository userRepository;
    private final AuthProperties properties;
    private final Clock clock;

    public boolean isLocked(User user) {
        return nonNull(user.getLockedUntil()) && user.getLockedUntil().isAfter(clock.instant());
    }

    @Transactional
    public void registerFailure(UUID userId) {
        userRepository.findByIdForUpdate(userId).ifPresent(user -> {
            int failures = user.getFailedLoginCount() + 1;
            if (failures >= properties.maxFailedLogins()) {
                Instant lockedUntil = clock.instant().plus(properties.lockoutDuration());
                user.setLockedUntil(lockedUntil);
                user.setFailedLoginCount(0);
                log.warn("[SECURITY] User {} locked until {} after {} failed logins", userId, lockedUntil, failures);
            } else {
                user.setFailedLoginCount(failures);
            }
        });
    }

    @Transactional
    public void registerSuccess(UUID userId) {
        userRepository.findByIdForUpdate(userId)
                .filter(user -> user.getFailedLoginCount() > 0 || nonNull(user.getLockedUntil()))
                .ifPresent(user -> {
                    user.setFailedLoginCount(0);
                    user.setLockedUntil(null);
                });
    }
}
