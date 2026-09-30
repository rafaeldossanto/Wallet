package com.wallet.core.identity.service;

import com.wallet.core.identity.dto.LoginRequest;
import com.wallet.core.identity.dto.RefreshTokenRequest;
import com.wallet.core.identity.dto.RegisterRequest;
import com.wallet.core.identity.dto.TokenResponse;
import com.wallet.core.identity.dto.UserResponse;
import com.wallet.core.identity.entity.User;
import com.wallet.core.identity.mapper.TokenMapper;
import com.wallet.core.identity.mapper.UserMapper;
import com.wallet.core.identity.repository.UserRepository;
import com.wallet.core.shared.error.DomainException;
import com.wallet.core.shared.error.ErrorType;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Locale;
import java.util.Optional;

import static java.util.Objects.nonNull;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final LoginAttemptService loginAttemptService;
    private final AccessTokenService accessTokenService;
    private final RefreshTokenService refreshTokenService;
    private final Clock clock;

    /**
     * Compared against when the e-mail does not exist, so an unknown e-mail takes as long as a
     * wrong password and response time does not reveal which accounts exist.
     */
    private final String timingEqualizerHash;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                       LoginAttemptService loginAttemptService, AccessTokenService accessTokenService,
                       RefreshTokenService refreshTokenService, Clock clock) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.loginAttemptService = loginAttemptService;
        this.accessTokenService = accessTokenService;
        this.refreshTokenService = refreshTokenService;
        this.clock = clock;
        this.timingEqualizerHash = passwordEncoder.encode("timing-equalizer");
    }

    @Transactional
    public UserResponse register(RegisterRequest request) {
        String email = normalizeEmail(request.email());
        if (userRepository.existsByEmail(email)) {
            throw emailTaken();
        }
        User user = UserMapper.toEntity(email, passwordEncoder.encode(request.password()),
                request.displayName(), clock.instant());
        try {
            return UserMapper.toResponse(userRepository.saveAndFlush(user));
        } catch (DataIntegrityViolationException ex) {
            // Two sign-ups with the same e-mail at the same time: the unique index decides.
            throw emailTaken();
        }
    }

    public TokenResponse login(LoginRequest request) {
        Optional<User> found = userRepository.findByEmail(normalizeEmail(request.email()));
        if (found.isEmpty()) {
            passwordEncoder.matches(request.password(), timingEqualizerHash);
            throw invalidCredentials();
        }
        User user = found.get();
        if (loginAttemptService.isLocked(user)) {
            throw new DomainException(ErrorType.LOCKED, "auth.locked",
                    "Too many failed logins; try again after " + user.getLockedUntil());
        }
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            loginAttemptService.registerFailure(user.getId());
            throw invalidCredentials();
        }
        if (user.getFailedLoginCount() > 0 || nonNull(user.getLockedUntil())) {
            loginAttemptService.registerSuccess(user.getId());
        }
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

    private static String normalizeEmail(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }

    private static DomainException emailTaken() {
        return new DomainException(ErrorType.CONFLICT, "auth.email_taken", "E-mail already registered");
    }

    private static DomainException invalidCredentials() {
        return new DomainException(ErrorType.UNAUTHORIZED, "auth.invalid_credentials", "Wrong e-mail or password");
    }

    private static DomainException invalidRefreshToken() {
        return new DomainException(ErrorType.UNAUTHORIZED, "auth.invalid_refresh_token",
                "Refresh token is unknown, expired or revoked");
    }
}
