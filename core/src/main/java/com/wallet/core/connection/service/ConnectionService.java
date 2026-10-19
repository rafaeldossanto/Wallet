package com.wallet.core.connection.service;

import com.wallet.core.connection.ConnectionLinked;
import com.wallet.core.connection.ConnectionRevocation;
import com.wallet.core.connection.RevocationReport;
import com.wallet.core.connection.config.ConnectionProperties;
import com.wallet.core.connection.dto.ConnectTokenResponse;
import com.wallet.core.connection.dto.ConnectionResponse;
import com.wallet.core.connection.dto.LinkConnectionRequest;
import com.wallet.core.connection.entity.Connection;
import com.wallet.core.connection.mapper.ConnectionMapper;
import com.wallet.core.connection.repository.ConnectionRepository;
import com.wallet.core.provider.FinancialDataProvider;
import com.wallet.core.provider.ProviderCallContext;
import com.wallet.core.provider.ProviderCallReason;
import com.wallet.core.provider.ProviderErrors;
import com.wallet.core.provider.ProviderItem;
import com.wallet.core.shared.error.DomainException;
import com.wallet.core.shared.error.ErrorType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static java.util.Objects.isNull;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConnectionService implements ConnectionRevocation {

    private final ConnectionRepository repository;
    private final FinancialDataProvider provider;
    private final ConnectionProperties properties;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    @Transactional
    public ConnectionResponse link(UUID userId, LinkConnectionRequest request) {
        String itemId = request.providerItemId();
        if (repository.existsByProviderAndProviderItemId(ConnectionMapper.PROVIDER_PLUGGY, itemId)) {
            throw alreadyLinked();
        }
        ProviderItem item = findItemOwnedBy(userId, itemId);
        try {
            Connection connection = repository.saveAndFlush(ConnectionMapper.toEntity(userId, item, clock.instant()));
            events.publishEvent(new ConnectionLinked(connection.getId(), userId));
            return ConnectionMapper.toResponse(connection);
        } catch (DataIntegrityViolationException ex) {
            throw alreadyLinked();
        }
    }

    /**
     * The widget creates the item for this user ({@code clientUserId}), so linking it afterwards
     * passes the ownership check in either connection mode.
     */
    public ConnectTokenResponse createConnectToken(UUID userId) {
        String connectToken = new ProviderCallContext(userId, null, null, ProviderCallReason.CONNECT)
                .call(() -> provider.createConnectToken(userId.toString(), properties.oauthRedirectUri()));
        return new ConnectTokenResponse(connectToken);
    }

    @Transactional(readOnly = true)
    public List<ConnectionResponse> list(UUID userId) {
        return repository.findByUserIdOrderByCreatedAtAsc(userId).stream().map(ConnectionMapper::toResponse).toList();
    }

    /**
     * Unlinking deletes every synced row of the connection (the database cascades). In production
     * the item is also deleted at Pluggy, which revokes the Open Finance consent. With Meu Pluggy the
     * item belongs to the Meu Pluggy account, so the Wallet only lets go of it.
     */
    @Transactional
    public void unlink(UUID userId, UUID connectionId) {
        Connection connection = repository.findByIdAndUserId(connectionId, userId).orElseThrow(ConnectionService::notFound);
        releaseAtProvider(userId, connection, ProviderCallReason.UNLINK);
        repository.delete(connection);
    }

    /**
     * Not transactional: the provider is called without holding the database. Unlike unlinking,
     * a provider failure does not stop anything; the account goes anyway, and the failure is
     * logged so the item can be revoked by hand at the provider.
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

    private static DomainException itemNotFound() {
        return new DomainException(ErrorType.NOT_FOUND, "connection.item_not_found", "No such item at the provider");
    }

    private static DomainException notFound() {
        return new DomainException(ErrorType.NOT_FOUND, "connection.not_found", "No such connection");
    }
}
