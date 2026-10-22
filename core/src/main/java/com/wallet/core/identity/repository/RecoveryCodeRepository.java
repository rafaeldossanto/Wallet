package com.wallet.core.identity.repository;

import com.wallet.core.identity.entity.RecoveryCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface RecoveryCodeRepository extends JpaRepository<RecoveryCode, UUID> {

    Optional<RecoveryCode> findByUserIdAndCodeHashAndUsedAtIsNull(UUID userId, String codeHash);

    long countByUserIdAndUsedAtIsNull(UUID userId);

    /** Compare-and-set: each code works once. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update RecoveryCode c set c.usedAt = :now where c.id = :id and c.usedAt is null")
    int markUsed(@Param("id") UUID id, @Param("now") Instant now);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from RecoveryCode c where c.userId = :userId")
    int deleteByUser(@Param("userId") UUID userId);
}
