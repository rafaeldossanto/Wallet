package com.wallet.core.identity.service;

import com.wallet.core.identity.dto.LoginRequest;
import com.wallet.core.identity.dto.RefreshTokenRequest;
import com.wallet.core.identity.dto.TokenResponse;
import com.wallet.core.identity.entity.User;
import com.wallet.core.identity.mapper.TokenMapper;
import com.wallet.core.identity.repository.UserRepository;
import com.wallet.core.shared.error.AccountLockedException;
import com.wallet.core.shared.error.DomainException;
import com.wallet.core.shared.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

import static org.springframework.util.StringUtils.hasText;

/**
 * Sign-in, refresh and logout.
 *
 * <p>Sign-in, in order: a locked e-mail is refused before any hash ({@link LoginThrottle}); then
 * one BCrypt, also for e-mails without an account ({@link PasswordHasher#matchNothing}); then the
 * second factor, when it is on. Every failure counts toward the lockout, and an e-mail without an
 * account, a wrong password and an unconfirmed account all answer {@code auth.invalid_credentials}:
 * nothing in the answer, or in its time, tells whether an account exists (finding A16).
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordHasher hasher;
    private final LoginThrottle loginThrottle;
    private final PasswordConfirmation passwordConfirmation;
    private final RegistrationService registration;
    private final TwoFactorService twoFactor;
    private final AccessTokenService accessTokenService;
    private final RefreshTokenService refreshTokenService;

    /** @param clientAddress where the BFF saw the request come from; scopes the lockout */
    public TokenResponse login(LoginRequest request, String clientAddress) {
        String email = Emails.normalize(request.email());
        loginThrottle.lockedUntil(email, clientAddress).ifPresent(until -> {
            throw new AccountLockedException(until);
        });
        Optional<User> found = userRepository.findByEmail(email);
        if (found.isEmpty()) {
            hasher.matchNothing(request.password());
            throw loginThrottle.registerFailure(email, clientAddress)
                    .<DomainException>map(lockout -> new AccountLockedException(lockout.until()))
                    .orElseGet(AuthService::invalidCredentials);
        }
        User user = found.get();
        if (!hasher.matches(request.password(), user.getPasswordHash())) {
            throw passwordConfirmation.failure(user, clientAddress, AuthService::invalidCredentials);
        }
        if (!user.isEmailConfirmed()) {
            registration.resendIfUnconfirmed(user);
            throw passwordConfirmation.failure(user, clientAddress, AuthService::invalidCredentials);
        }
        if (user.isTwoFactorEnabled()) {
            if (!hasText(request.code())) {
                // Only someone with the right password gets here.
                throw new DomainException(ErrorType.UNAUTHORIZED, "auth.two_factor_required",
                        "Send the authenticator code (or a recovery code) as code");
            }
            if (!twoFactor.verify(user, request.code())) {
                throw passwordConfirmation.failure(user, clientAddress, () -> TwoFactorService.wrongCode(ErrorType.UNAUTHORIZED));
            }
        }
        loginThrottle.registerSuccess(email, clientAddress);
        return TokenMapper.toResponse(accessTokenService.issue(user), refreshTokenService.issueForNewLogin(user.getId()));
    }

    public TokenResponse refresh(RefreshTokenRequest request) {
        return switch (refreshTokenService.rotate(request.refreshToken())) {
            case RotationResult.Rotated(var userId, var refreshToken) -> userRepository.findById(userId)
                    .map(user -> TokenMapper.toResponse(accessTokenService.issue(user), refreshToken))
                    .orElseThrow(AuthService::invalidRefreshToken);
            case RotationResult.Reused() -> throw new DomainException(ErrorType.UNAUTHORIZED,
                    "auth.refresh_reused", "Refresh token was already used; session revoked");
            case RotationResult.Invalid() -> throw invalidRefreshToken();
        };
    }

    /** Always succeeds: logging out with an unknown token must not reveal anything. */
    public void logout(RefreshTokenRequest request) {
        refreshTokenService.revokeFamilyOf(request.refreshToken());
    }

    private static DomainException invalidCredentials() {
        return new DomainException(ErrorType.UNAUTHORIZED, "auth.invalid_credentials", "Wrong e-mail or password");
    }

    private static DomainException invalidRefreshToken() {
        return new DomainException(ErrorType.UNAUTHORIZED, "auth.invalid_refresh_token",
                "Refresh token is unknown, expired or revoked");
    }
}
