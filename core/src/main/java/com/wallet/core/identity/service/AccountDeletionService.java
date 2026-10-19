package com.wallet.core.identity.service;

import com.wallet.core.connection.ConnectionRevocation;
import com.wallet.core.connection.RevocationReport;
import com.wallet.core.identity.entity.User;
import com.wallet.core.identity.repository.UserRepository;
import com.wallet.core.shared.error.DomainException;
import com.wallet.core.shared.error.ErrorType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Deleting an account (LGPD, art. 18, VI): the user and everything synced or typed in goes.
 *
 * <p>The password comes first: an access token alone, stolen or left on an unlocked phone, must
 * not be enough to erase an account. A wrong one counts toward the lockout like a failed login.
 *
 * <p>Order matters. First every connection is revoked at the provider, outside any transaction,
 * while the Wallet still knows the items; a provider failure is logged and does not stop the
 * deletion. Then, in one transaction with the user's row locked, connections linked in the
 * meantime are revoked too and the user row is deleted. The schema cascades from {@code users}
 * over connections, accounts, transactions, balance snapshots, card bills, investments and their
 * snapshots, sync runs, planning, dismissed suggestions and refresh tokens.
 *
 * <p>{@code provider_calls} is kept on purpose: it has no foreign key, holds no financial data,
 * and its 180-day retention also covers the record that the items were revoked.
 */
@Slf4j
@Service
public class AccountDeletionService {

    private final UserRepository userRepository;
    private final ConnectionRevocation connections;
    private final PasswordEncoder passwordEncoder;
    private final LoginAttemptService loginAttemptService;
    private final TransactionTemplate transactions;

    public AccountDeletionService(UserRepository userRepository, ConnectionRevocation connections,
                                  PasswordEncoder passwordEncoder, LoginAttemptService loginAttemptService,
                                  PlatformTransactionManager transactionManager) {
        this.userRepository = userRepository;
        this.connections = connections;
        this.passwordEncoder = passwordEncoder;
        this.loginAttemptService = loginAttemptService;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    /** Idempotent: an account that is already gone is nothing to do. */
    public void delete(UUID userId, String password) {
        Optional<User> user = userRepository.findById(userId);
        if (user.isEmpty()) {
            return;
        }
        confirm(user.get(), password);
        RevocationReport before = connections.revokeAll(userId, Set.of());
        Optional<RevocationReport> done = transactions.execute(status -> deleteLocked(userId, before));
        done.ifPresent(report -> log.info("[SECURITY] Account of user {} deleted: {} connections revoked at the provider, "
                        + "{} only let go of, {} not revoked (provider failed)",
                userId, report.revoked(), report.released(), report.failed()));
    }

    /**
     * 403 and not 401 for a wrong password: the BFF relays the core's 401 as an expired session and
     * the app would log the user out. A locked account answers as at login.
     */
    private void confirm(User user, String password) {
        if (loginAttemptService.isLocked(user)) {
            throw new DomainException(ErrorType.LOCKED, "auth.locked",
                    "Too many failed logins; try again after " + user.getLockedUntil());
        }
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            loginAttemptService.registerFailure(user.getId());
            throw new DomainException(ErrorType.FORBIDDEN, "auth.wrong_password", "The password does not match");
        }
    }

    /**
     * Locking the user's row ({@code FOR UPDATE}) makes a concurrent link wait until the commit: a
     * new connection needs that row for its foreign key, and once the user is gone it fails. So no
     * connection slips in between the last revocation and the delete.
     */
    private Optional<RevocationReport> deleteLocked(UUID userId, RevocationReport before) {
        Optional<User> user = userRepository.findByIdLockingReferences(userId);
        if (user.isEmpty()) {
            return Optional.empty();
        }
        RevocationReport linkedMeanwhile = connections.revokeAll(userId, before.connectionIds());
        userRepository.delete(user.get());
        return Optional.of(before.plus(linkedMeanwhile));
    }
}
