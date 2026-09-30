package com.wallet.core.identity.repository;

import com.wallet.core.identity.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /**
     * Compare-and-set: only one caller can rotate a token. The loser gets 0 and treats it as
     * reuse, the same as the Storage project does.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update RefreshToken t
               set t.rotatedAt = :now, t.replacedById = :successorId
             where t.id = :id and t.rotatedAt is null and t.revokedAt is null
            """)
    int markRotated(@Param("id") UUID id, @Param("successorId") UUID successorId, @Param("now") Instant now);

    /** Compare-and-set on the successor, used by the lost-response tolerance. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update RefreshToken t
               set t.replacedById = :newSuccessorId
             where t.id = :id and t.replacedById = :currentSuccessorId
            """)
    int replaceSuccessor(@Param("id") UUID id,
                         @Param("currentSuccessorId") UUID currentSuccessorId,
                         @Param("newSuccessorId") UUID newSuccessorId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update RefreshToken t set t.revokedAt = :now where t.id = :id and t.revokedAt is null")
    int revoke(@Param("id") UUID id, @Param("now") Instant now);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update RefreshToken t set t.revokedAt = :now where t.familyId = :familyId and t.revokedAt is null")
    int revokeFamily(@Param("familyId") UUID familyId, @Param("now") Instant now);
}
