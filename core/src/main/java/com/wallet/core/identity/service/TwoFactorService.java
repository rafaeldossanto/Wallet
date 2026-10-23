package com.wallet.core.identity.service;

import com.wallet.core.identity.dto.RecoveryCodesResponse;
import com.wallet.core.identity.dto.TwoFactorSetupResponse;
import com.wallet.core.identity.entity.User;
import com.wallet.core.identity.mapper.RecoveryCodeMapper;
import com.wallet.core.identity.repository.RecoveryCodeRepository;
import com.wallet.core.identity.repository.UserRepository;
import com.wallet.core.shared.crypto.DataCipher;
import com.wallet.core.shared.error.DomainException;
import com.wallet.core.shared.error.ErrorType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;

import static java.util.Objects.isNull;

/**
 * The second factor: TOTP (RFC 6238), so any authenticator app works and it behaves the same in
 * the browser, on Android and on Windows (a passkey would need WebAuthn on each of them).
 *
 * <p>Turning it on takes three steps: the password (a stolen access token must not be able to tie
 * the account to the thief's phone), the QR code, and one code from the app, which proves the app
 * has the secret before sign-ins start asking for it. Then come ten single-use recovery codes,
 * shown once. Turning it off takes the password and a code (or a recovery code).
 *
 * <p>The secret is encrypted with the data key ({@link DataCipher}), like the transaction
 * descriptions: a database dump alone does not give out the codes.
 */
@Slf4j
@Service
public class TwoFactorService {

    static final String ISSUER = "Wallet";

    private final UserRepository users;
    private final RecoveryCodeRepository recoveryCodes;
    private final DataCipher cipher;
    private final PasswordConfirmation passwordConfirmation;
    private final Clock clock;
    private final TransactionTemplate transactions;

    public TwoFactorService(UserRepository users, RecoveryCodeRepository recoveryCodes, DataCipher cipher,
                            PasswordConfirmation passwordConfirmation, Clock clock,
                            PlatformTransactionManager transactionManager) {
        this.users = users;
        this.recoveryCodes = recoveryCodes;
        this.cipher = cipher;
        this.passwordConfirmation = passwordConfirmation;
        this.clock = clock;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    /** A new secret, not active yet. Starting over before {@link #enable} replaces it. */
    public TwoFactorSetupResponse setUp(UUID userId, String password, String clientAddress) {
        User user = existing(userId);
        if (user.isTwoFactorEnabled()) {
            throw alreadyEnabled();
        }
        passwordConfirmation.confirm(user, password, clientAddress);
        String secret = Base32.encode(Totp.newSecret());
        transactions.executeWithoutResult(status -> {
            User locked = users.findByIdForUpdate(userId).orElseThrow(TwoFactorService::unauthenticated);
            if (locked.isTwoFactorEnabled()) {
                throw alreadyEnabled();
            }
            locked.setTotpSecret(cipher.encrypt(secret));
            locked.setTotpLastStep(null);
        });
        return new TwoFactorSetupResponse(secret, Totp.uri(ISSUER, user.getEmail(), secret));
    }

    /** The first code from the app turns it on and brings the recovery codes, the only time they are shown. */
    public RecoveryCodesResponse enable(UUID userId, String code) {
        return transactions.execute(status -> {
            User user = users.findByIdForUpdate(userId).orElseThrow(TwoFactorService::unauthenticated);
            if (user.isTwoFactorEnabled()) {
                throw alreadyEnabled();
            }
            byte[] secret = secretOf(user).orElseThrow(() -> new DomainException(ErrorType.CONFLICT,
                    "two_factor.not_set_up", "Start the set-up first"));
            Instant now = clock.instant();
            OptionalLong step = Totp.matchingStep(secret, digitsOf(code), now);
            if (step.isEmpty()) {
                throw new DomainException(ErrorType.UNPROCESSABLE, "two_factor.invalid_code",
                        "The code does not match; check the phone's clock and try the next one");
            }
            user.setTotpEnabledAt(now);
            user.setTotpLastStep(step.getAsLong());
            recoveryCodes.deleteByUser(userId);
            List<String> codes = RecoveryCodes.generate();
            recoveryCodes.saveAll(codes.stream()
                    .map(recoveryCode -> RecoveryCodeMapper.toEntity(userId, RecoveryCodes.hash(recoveryCode), now))
                    .toList());
            log.info("[SECURITY] User {} turned the second factor on", userId);
            return new RecoveryCodesResponse(codes);
        });
    }

    public void disable(UUID userId, String password, String code, String clientAddress) {
        User user = existing(userId);
        if (!user.isTwoFactorEnabled()) {
            throw new DomainException(ErrorType.CONFLICT, "two_factor.not_enabled", "The second factor is off");
        }
        passwordConfirmation.confirm(user, password, clientAddress);
        if (!verify(user, code)) {
            // 403, not 401: with a session open, the app reads 401 as the session having expired.
            throw passwordConfirmation.failure(user, clientAddress, () -> wrongCode(ErrorType.FORBIDDEN));
        }
        transactions.executeWithoutResult(status -> {
            users.findByIdForUpdate(userId).ifPresent(locked -> {
                locked.setTotpSecret(null);
                locked.setTotpEnabledAt(null);
                locked.setTotpLastStep(null);
            });
            recoveryCodes.deleteByUser(userId);
        });
        log.info("[SECURITY] User {} turned the second factor off", userId);
    }

    /**
     * An authenticator code, good once ({@code totp_last_step}), or an unused recovery code, which
     * is then spent.
     */
    public boolean verify(User user, String code) {
        String typed = code.strip();
        Instant now = clock.instant();
        Boolean accepted = transactions.execute(status -> {
            if (RecoveryCodes.isAuthenticatorCode(typed)) {
                OptionalLong step = secretOf(user)
                        .map(secret -> Totp.matchingStep(secret, digitsOf(typed), now))
                        .orElse(OptionalLong.empty());
                return step.isPresent() && users.acceptTotpStep(user.getId(), step.getAsLong()) == 1;
            }
            return recoveryCodes.findByUserIdAndCodeHashAndUsedAtIsNull(user.getId(), RecoveryCodes.hash(typed))
                    .filter(recoveryCode -> recoveryCodes.markUsed(recoveryCode.getId(), now) == 1)
                    .isPresent();
        });
        return Boolean.TRUE.equals(accepted);
    }

    public static DomainException wrongCode(ErrorType type) {
        return new DomainException(type, "auth.invalid_two_factor_code",
                "The authenticator or recovery code does not match");
    }

    /** Empty when there is none, or when it no longer decrypts (written with another data key). */
    private Optional<byte[]> secretOf(User user) {
        if (isNull(user.getTotpSecret())) {
            return Optional.empty();
        }
        String secret = cipher.decrypt(user.getTotpSecret());
        if (isNull(secret)) {
            log.warn("[SECURITY] The TOTP secret of user {} does not decrypt; only recovery codes work", user.getId());
            return Optional.empty();
        }
        return Optional.of(Base32.decode(secret));
    }

    private User existing(UUID userId) {
        return users.findById(userId).orElseThrow(TwoFactorService::unauthenticated);
    }

    private static String digitsOf(String code) {
        return code.replaceAll("\\s", "");
    }

    private static DomainException alreadyEnabled() {
        return new DomainException(ErrorType.CONFLICT, "two_factor.already_enabled", "The second factor is already on");
    }

    private static DomainException unauthenticated() {
        return new DomainException(ErrorType.UNAUTHORIZED, "auth.unauthenticated", "User of this token no longer exists");
    }
}
