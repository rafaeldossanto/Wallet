package com.wallet.core.identity.repository;

import com.wallet.core.identity.entity.EmailToken;
import com.wallet.core.identity.entity.EmailTokenPurpose;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface EmailTokenRepository extends JpaRepository<EmailToken, UUID> {

    Optional<EmailToken> findByTokenHash(String tokenHash);

    long countByUserIdAndPurposeAndCreatedAtAfter(UUID userId, EmailTokenPurpose purpose, Instant after);

    /** The open link of this purpose, if any: Settings shows the address waiting for confirmation. */
    Optional<EmailToken> findFirstByUserIdAndPurposeAndUsedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(
            UUID userId, EmailTokenPurpose purpose, Instant now);

    /** Compare-and-set: a link opens once, even with two clicks at the same time. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update EmailToken t set t.usedAt = :now where t.id = :id and t.usedAt is null")
    int markUsed(@Param("id") UUID id, @Param("now") Instant now);

    /** Only the newest link of a purpose works: the ones sent before stop when another goes out. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update EmailToken t set t.usedAt = :now
             where t.userId = :userId and t.purpose = :purpose and t.usedAt is null
            """)
    int retireOpen(@Param("userId") UUID userId, @Param("purpose") EmailTokenPurpose purpose, @Param("now") Instant now);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from EmailToken t where t.expiresAt < :before")
    int deleteExpiredBefore(@Param("before") Instant before);
}
