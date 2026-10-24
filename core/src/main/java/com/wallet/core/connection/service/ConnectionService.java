package com.wallet.core.connection.service;

import com.wallet.core.connection.ConnectionLinked;
import com.wallet.core.connection.ConnectionRenewed;
import com.wallet.core.connection.ConnectionStatus;
import com.wallet.core.connection.ConsentValidity;
import com.wallet.core.connection.ConnectionRevocation;
import com.wallet.core.connection.RevocationReport;
import com.wallet.core.connection.config.ConnectionProperties;
import com.wallet.core.connection.dto.ConnectTokenRequest;
import com.wallet.core.connection.dto.ConnectTokenResponse;
import com.wallet.core.connection.dto.ConnectionResponse;
import com.wallet.core.connection.dto.LinkConnectionRequest;
import com.wallet.core.connection.dto.RenewConnectionRequest;
import com.wallet.core.connection.entity.Connection;
import com.wallet.core.connection.mapper.ConnectionMapper;
import com.wallet.core.connection.repository.ConnectionConsentRepository;
import com.wallet.core.connection.repository.ConnectionRepository;
import com.wallet.core.connection.repository.UserLinkLock;
import com.wallet.core.provider.FinancialDataProvider;
import com.wallet.core.provider.ProviderCallContext;
import com.wallet.core.provider.ProviderCallReason;
import com.wallet.core.provider.ProviderErrors;
import com.wallet.core.provider.ProviderItem;
import com.wallet.core.shared.error.DomainException;
import com.wallet.core.shared.error.ErrorType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static java.util.Objects.isNull;

@Slf4j
@Service
public class ConnectionService implements ConnectionRevocation {

    private final ConnectionRepository repository;
    private final ConnectionConsentRepository consents;
    private final UserLinkLock linkLock;
    private final FinancialDataProvider provider;
    private final ConnectionProperties properties;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final TransactionTemplate transactions;

    public ConnectionService(ConnectionRepository repository, ConnectionConsentRepository consents, UserLinkLock linkLock, FinancialDataProvider provider,
                             ConnectionProperties properties, ApplicationEventPublisher events, Clock clock,
                             PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.consents = consents;
        this.linkLock = linkLock;
        this.provider = provider;
        this.properties = properties;
        this.events = events;
        this.clock = clock;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    /**
     * Whether an item is already linked is only told to its owner. An item somebody else linked
     * answers exactly like one that does not exist, after the same call to the provider, so the
     * answer cannot be used to probe for other people's items.
     *
     * <p>The provider is called outside any transaction, as in the sync: a slow Pluggy must not hold
     * a database connection, or one user linking several banks at once would take the pool from
     * everyone. So the checks run twice: before the call, so that an item the user already linked,
     * or a user at the ceiling, costs no call to the provider; and again in the short transaction
     * that writes, under the user's link lock ({@link UserLinkLock}), so that parallel requests
     * cannot all get past the ceiling.
     */
    public ConnectionResponse link(UUID userId, LinkConnectionRequest request) {
        String itemId = request.providerItemId();
        boolean linkedByAnother = checkLinkable(userId, itemId);
        ProviderItem item = findItemOwnedBy(userId, itemId);
        if (linkedByAnother) {
            log.warn("[SECURITY] User {} tried to link item {} linked by another user", userId, itemId);
            throw itemNotFound();
        }
        try {
            return transactions.execute(status -> {
                linkLock.acquire(userId);
                if (checkLinkable(userId, itemId)) {
                    // Another user linked it while the provider was answering.
                    throw itemNotFound();
                }
                Instant now = clock.instant();
                Connection connection = repository.saveAndFlush(ConnectionMapper.toEntity(userId, item, now));
                consents.save(ConnectionMapper.toConsent(userId, connection.getId(), request.consentVersion(), now,
                        item.consentExpiresAt()));
                events.publishEvent(new ConnectionLinked(connection.getId(), userId));
                return ConnectionMapper.toResponse(connection);
            });
        } catch (DataIntegrityViolationException ex) {
            // Somebody else linked it after the check (only the user's own links wait on the lock),
            // or the account is being deleted: either way the item is not this user's to link.
            throw itemNotFound();
        }
    }

    /**
     * Throws when this user already linked the item, or already holds as many connections as allowed.
     *
     * @return whether somebody else linked the item: the caller answers as for an unknown item
     */
    private boolean checkLinkable(UUID userId, String itemId) {
        Optional<Connection> linked = repository.findByProviderAndProviderItemId(ConnectionMapper.PROVIDER_PLUGGY, itemId);
        if (linked.isPresent() && linked.get().getUserId().equals(userId)) {
            throw alreadyLinked();
        }
        if (repository.countByUserId(userId) >= properties.maxConnectionsPerUser()) {
            throw limitReached();
        }
        return linked.isPresent();
    }

    /**
     * The widget creates the item for this user ({@code clientUserId}), so linking it afterwards
     * passes the ownership check in either connection mode.
     */
    public ConnectTokenResponse createConnectToken(UUID userId, ConnectTokenRequest request) {
        // Renewing: the widget opens in update mode, on the item of the connection (which is the user's).
        Connection renewing = isNull(request.connectionId()) ? null
                : repository.findByIdAndUserId(request.connectionId(), userId).orElseThrow(ConnectionService::notFound);
        String itemId = isNull(renewing) ? null : renewing.getProviderItemId();
        String connectToken = new ProviderCallContext(userId, isNull(renewing) ? null : renewing.getId(), itemId,
                ProviderCallReason.CONNECT)
                .call(() -> provider.createConnectToken(userId.toString(), properties.oauthRedirectUri(), itemId));
        return new ConnectTokenResponse(connectToken);
    }

    /**
     * The user went through the widget again for a connection whose authorization ended (or is
     * about to): reads the item at the provider, outside any transaction, and if its date really
     * moved on, records the new consent, puts the connection back to ACTIVE with the new date and
     * starts a sync. A date that did not move (the widget was closed) is 409
     * {@code connection.consent_not_renewed} and changes nothing.
     */
    public ConnectionResponse renew(UUID userId, UUID connectionId, RenewConnectionRequest request) {
        Connection connection = repository.findByIdAndUserId(connectionId, userId).orElseThrow(ConnectionService::notFound);
        ProviderItem item = new ProviderCallContext(userId, connectionId, connection.getProviderItemId(), ProviderCallReason.RENEW)
                .call(() -> provider.findItem(connection.getProviderItemId()));
        return transactions.execute(status -> {
            Connection locked = repository.findLockedById(connectionId)
                    .filter(found -> found.getUserId().equals(userId)).orElseThrow(ConnectionService::notFound);
            Instant now = clock.instant();
            if (!ConsentValidity.renewed(locked.getConsentExpiresAt(), item.consentExpiresAt(), now)) {
                throw new DomainException(ErrorType.CONFLICT, "connection.consent_not_renewed",
                        "The provider still reports the old authorization; finish the renewal in the bank's flow");
            }
            locked.setConsentExpiresAt(item.consentExpiresAt());
            locked.setStatus(ConnectionStatus.ACTIVE);
            locked.setUpdatedAt(now);
            consents.save(ConnectionMapper.toConsent(userId, connectionId, request.consentVersion(), now, item.consentExpiresAt()));
            events.publishEvent(new ConnectionRenewed(connectionId, userId));
            log.info("[CONNECTION] Connection {} of user {} renewed until {}", connectionId, userId, item.consentExpiresAt());
            return ConnectionMapper.toResponse(locked);
        });
    }

    @Transactional(readOnly = true)
    public List<ConnectionResponse> list(UUID userId) {
        return repository.findByUserIdOrderByCreatedAtAsc(userId).stream().map(ConnectionMapper::toResponse).toList();
    }

    /**
     * Unlinking deletes every synced row of the connection (the database cascades). In production
     * the item is also deleted at Pluggy, which revokes the Open Finance consent. With Meu Pluggy the
     * item belongs to the Meu Pluggy account, so the Wallet only lets go of it.
     *
     * <p>The provider is called outside any transaction, as when linking: a slow Pluggy must not
     * hold a database connection. A provider failure still stops the unlink, since the row is only
     * deleted afterwards, in a short transaction of its own. Gone by then (unlinked twice at once,
     * or the account deleted meanwhile) is nothing left to do.
     */
    public void unlink(UUID userId, UUID connectionId) {
        Connection connection = repository.findByIdAndUserId(connectionId, userId).orElseThrow(ConnectionService::notFound);
        releaseAtProvider(userId, connection, ProviderCallReason.UNLINK);
        transactions.executeWithoutResult(status -> repository.findByIdAndUserId(connectionId, userId).ifPresent(found -> {
            // The proof that the user took the authorization back, before the connection goes: the
            // consent rows have no foreign key to it, so they stay (see V13).
            consents.revokeAllOf(connectionId, clock.instant());
            repository.delete(found);
        }));
    }

    /**
     * Not transactional: the provider is called without holding the database. Like unlinking; but
     * unlike it, a provider failure does not stop anything: the account goes anyway, and the
     * failure is logged so the item can be revoked by hand at the provider.
     */
    @Override
    public RevocationReport revokeAll(UUID userId, Set<UUID> skip) {
        Set<UUID> handled = new LinkedHashSet<>();
        int revoked = 0;
        int released = 0;
        int failed = 0;
        for (Connection connection : repository.findByUserIdOrderByCreatedAtAsc(userId)) {
            if (skip.contains(connection.getId())) {
                continue;
            }
            handled.add(connection.getId());
            try {
                if (releaseAtProvider(userId, connection, ProviderCallReason.ACCOUNT_DELETION)) {
                    revoked++;
                } else {
                    released++;
                }
            } catch (RuntimeException ex) {
                failed++;
                log.warn("[SECURITY] Account deletion of user {}: connection {} not revoked at the provider ({})",
                        userId, connection.getId(), codeOf(ex));
            }
        }
        return new RevocationReport(handled, revoked, released, failed);
    }

    /**
     * Revokes the item at the provider when it is the Wallet's to revoke: always in production, and
     * while developing only items the widget created for this user.
     *
     * @return true when revoked (or already gone there), false when only let go of
     */
    private boolean releaseAtProvider(UUID userId, Connection connection, ProviderCallReason reason) {
        String itemId = connection.getProviderItemId();
        return new ProviderCallContext(userId, connection.getId(), itemId, reason).call(() -> {
            if (properties.isPluggyConnect() || createdByTheWidgetFor(userId, itemId)) {
                revokeAtProvider(itemId);
                return true;
            }
            return false;
        });
    }

    private static String codeOf(RuntimeException ex) {
        return ex instanceof DomainException domain ? domain.getCode() : ex.getClass().getSimpleName();
    }

    /**
     * While developing, Meu Pluggy items and widget items live side by side. A widget item holds a
     * consent the Wallet asked for, so it is revoked as in production. When Pluggy cannot say whose
     * item it is, the Wallet only lets go of it rather than refuse to unlink.
     */
    private boolean createdByTheWidgetFor(UUID userId, String itemId) {
        try {
            return userId.toString().equals(provider.findItem(itemId).clientUserId());
        } catch (DomainException ex) {
            log.warn("[CONNECTION] Item {} unlinked without revoking it: the provider did not answer ({})", itemId, ex.getCode());
            return false;
        }
    }

    /**
     * An item created by the Pluggy Connect widget belongs to one Wallet user ({@code clientUserId});
     * somebody else's item looks exactly like one that does not exist. Meu Pluggy items carry no
     * user, so they are accepted only while developing (MEU_PLUGGY), where widget items may be
     * linked too.
     */
    private ProviderItem findItemOwnedBy(UUID userId, String itemId) {
        ProviderItem item;
        try {
            item = new ProviderCallContext(userId, null, itemId, ProviderCallReason.LINK)
                    .call(() -> provider.findItem(itemId));
        } catch (DomainException ex) {
            if (ProviderErrors.NOT_FOUND.equals(ex.getCode())) {
                throw itemNotFound();
            }
            throw ex;
        }
        String owner = item.clientUserId();
        boolean ownedByAnother = isNull(owner) ? properties.isPluggyConnect() : !userId.toString().equals(owner);
        if (ownedByAnother) {
            log.warn("[SECURITY] User {} tried to link item {} created for another user", userId, itemId);
            throw itemNotFound();
        }
        return item;
    }

    private void revokeAtProvider(String itemId) {
        try {
            provider.deleteItem(itemId);
        } catch (DomainException ex) {
            if (!ProviderErrors.NOT_FOUND.equals(ex.getCode())) {
                throw ex;
            }
        }
    }

    private static DomainException alreadyLinked() {
        return new DomainException(ErrorType.CONFLICT, "connection.already_linked", "This item is already linked");
    }

    private DomainException limitReached() {
        return new DomainException(ErrorType.UNPROCESSABLE, "connection.limit_reached",
                "At most " + properties.maxConnectionsPerUser() + " connections per user");
    }

    private static DomainException itemNotFound() {
        return new DomainException(ErrorType.NOT_FOUND, "connection.item_not_found", "No such item at the provider");
    }

    private static DomainException notFound() {
        return new DomainException(ErrorType.NOT_FOUND, "connection.not_found", "No such connection");
    }
}
