package com.wallet.core.identity.service;

import com.wallet.core.identity.config.LegalProperties;
import com.wallet.core.identity.entity.User;
import com.wallet.core.identity.repository.UserRepository;
import com.wallet.core.shared.error.DomainException;
import com.wallet.core.shared.error.ErrorType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

/**
 * Acceptance of the Terms of Use and the Privacy Policy by someone who already has an account (LGPD
 * L15): the accounts from before the acceptance existed, and everyone when the texts change. Sign-up
 * records its own, in {@link RegistrationService}.
 *
 * <p>Only the app enforces it for now: the server tells whether acceptance is required
 * ({@code termsAcceptanceRequired} in the profile) and does not block any route. A conscious choice.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TermsService {

    private final UserRepository users;
    private final LegalProperties legal;
    private final Clock clock;

    /**
     * Idempotent for the current version. A different one means the user read an older text (the texts
     * changed meanwhile): 409, and the app reloads the profile to ask again.
     */
    @Transactional
    public void accept(UUID userId, String version) {
        if (!legal.termsVersion().equals(version)) {
            throw new DomainException(ErrorType.CONFLICT, "legal.terms_outdated",
                    "The Terms of Use and the Privacy Policy changed: read the current ones");
        }
        User user = users.findByIdForUpdate(userId).orElseThrow(TermsService::unauthenticated);
        if (!version.equals(user.getTermsVersion())) {
            user.setTermsVersion(version);
            user.setTermsAcceptedAt(clock.instant());
            log.info("[LEGAL] User {} accepted the terms, version {}", userId, version);
        }
    }

    /** A valid token for a user that no longer exists: treat as logged out. */
    private static DomainException unauthenticated() {
        return new DomainException(ErrorType.UNAUTHORIZED, "auth.unauthenticated", "User of this token no longer exists");
    }
}
