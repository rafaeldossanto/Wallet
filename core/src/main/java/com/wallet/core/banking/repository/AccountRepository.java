package com.wallet.core.banking.repository;

import com.wallet.core.banking.entity.Account;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AccountRepository extends JpaRepository<Account, UUID> {

    Optional<Account> findByConnectionIdAndProviderAccountId(UUID connectionId, String providerAccountId);

    List<Account> findByUserIdOrderByCreatedAtAsc(UUID userId);

    Optional<Account> findByIdAndUserId(UUID id, UUID userId);
}
