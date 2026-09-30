package com.wallet.core.connection.service;

import com.wallet.core.connection.ConnectionLinked;
import com.wallet.core.connection.config.ConnectionProperties;
import com.wallet.core.connection.dto.ConnectionResponse;
import com.wallet.core.connection.dto.LinkConnectionRequest;
import com.wallet.core.connection.entity.Connection;
import com.wallet.core.connection.mapper.ConnectionMapper;
import com.wallet.core.connection.repository.ConnectionRepository;
import com.wallet.core.provider.FinancialDataProvider;
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
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConnectionService {

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
        if (properties.isPluggyConnect()) {
            revokeAtProvider(connection.getProviderItemId());
        }
        repository.delete(connection);
    }

    /**
     * In production each item was created for one Wallet user ({@code clientUserId}); somebody else's
     * item looks exactly like one that does not exist. Meu Pluggy items carry no user.
     */
    private ProviderItem findItemOwnedBy(UUID userId, String itemId) {
        ProviderItem item;
        try {
            item = provider.findItem(itemId);
        } catch (DomainException ex) {
            if (ProviderErrors.NOT_FOUND.equals(ex.getCode())) {
                throw itemNotFound();
            }
            throw ex;
        }
        if (properties.isPluggyConnect() && !userId.toString().equals(item.clientUserId())) {
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
