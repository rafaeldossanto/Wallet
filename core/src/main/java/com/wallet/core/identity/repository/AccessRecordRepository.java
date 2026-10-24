package com.wallet.core.identity.repository;

import com.wallet.core.identity.entity.AccessRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;

/** Writes and cleans only: nothing reads access records but a court order, in the database. */
public interface AccessRecordRepository extends JpaRepository<AccessRecord, UUID> {

    /** Records older than the 6 months the law asks for. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from AccessRecord r where r.occurredAt < :before")
    int deleteOccurredBefore(@Param("before") Instant before);
}
