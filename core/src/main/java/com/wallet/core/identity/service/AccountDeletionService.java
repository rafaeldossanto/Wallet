package com.wallet.core.identity.service;

import com.wallet.core.connection.ConnectionRevocation;
import com.wallet.core.connection.RevocationReport;
import com.wallet.core.identity.entity.User;
import com.wallet.core.identity.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Deleting an account (LGPD, art. 18, VI): the user and everything synced or typed in goes.
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
    private final TransactionTemplate transactions;

    public AccountDeletionService(UserRepository userRepository, ConnectionRevocation connections,
                                  PlatformTransactionManager transactionManager) {
        this.userRepository = userRepository;
        this.connections = connections;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    /** Idempotent: an account that is already gone is nothing to do. */
    public void delete(UUID userId) {
        if (!userRepository.existsById(userId)) {
            return;
        }
        RevocationReport before = connections.revokeAll(userId, Set.of());
        Optional<RevocationReport> done = transactions.execute(status -> deleteLocked(userId, before));
        done.ifPresent(report -> log.info("[SECURITY] Account of user {} deleted: {} connections revoked at the provider, "
                        + "{} only let go of, {} not revoked (provider failed)",
                userId, report.revoked(), report.released(), report.failed()));
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
