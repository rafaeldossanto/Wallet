package com.wallet.core.connection.repository;

import com.wallet.core.connection.entity.ConnectionConsent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;

public interface ConnectionConsentRepository extends JpaRepository<ConnectionConsent, UUID> {

    /** Everything the user had granted for the connection ends now; already revoked ones keep their date. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update ConnectionConsent c set c.revokedAt = :now where c.connectionId = :connectionId and c.revokedAt is null")
    int revokeAllOf(@Param("connectionId") UUID connectionId, @Param("now") Instant now);
}
