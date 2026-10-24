package com.wallet.core.waitlist.repository;

import com.wallet.core.waitlist.entity.WaitlistEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WaitlistRepository extends JpaRepository<WaitlistEntry, UUID> {

    Optional<WaitlistEntry> findByConfirmTokenHash(String confirmTokenHash);

    /** Two sign-ups of the same address at the same time: one inserts (1), the other does nothing (0). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            insert into waitlist_entries (id, email, status, consent_version, created_at,
                                          confirm_token_hash, confirm_token_expires_at, confirm_sent_at)
            values (:id, :email, 'PENDING', :consentVersion, :now, :tokenHash, :expiresAt, :now)
            on conflict (email) do nothing
            """, nativeQuery = true)
    int insertPendingIfAbsent(@Param("id") UUID id, @Param("email") String email,
                              @Param("consentVersion") String consentVersion, @Param("now") Instant now,
                              @Param("tokenHash") String tokenHash, @Param("expiresAt") Instant expiresAt);

    /**
     * A new link for an entry nobody confirmed, if the last one went {@code sentBefore} or earlier;
     * the older link stops working. Compare-and-set: two requests at once send one.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            update waitlist_entries
               set confirm_token_hash = :tokenHash, confirm_token_expires_at = :expiresAt, confirm_sent_at = :now
             where email = :email and status = 'PENDING'
               and (confirm_sent_at is null or confirm_sent_at <= :sentBefore)
            """, nativeQuery = true)
    int renewPending(@Param("email") String email, @Param("tokenHash") String tokenHash,
                     @Param("expiresAt") Instant expiresAt, @Param("now") Instant now,
                     @Param("sentBefore") Instant sentBefore);

    /** Compare-and-set: the entry is confirmed, and welcomed, once. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            update waitlist_entries
               set status = 'CONFIRMED', confirmed_at = :now, unsubscribe_token = :unsubscribeToken
             where id = :id and status = 'PENDING'
            """, nativeQuery = true)
    int confirm(@Param("id") UUID id, @Param("now") Instant now, @Param("unsubscribeToken") String unsubscribeToken);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "delete from waitlist_entries where unsubscribe_token = :unsubscribeToken", nativeQuery = true)
    int deleteByUnsubscribeToken(@Param("unsubscribeToken") String unsubscribeToken);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "delete from waitlist_entries where status = 'PENDING' and created_at < :before", nativeQuery = true)
    int deletePendingCreatedBefore(@Param("before") Instant before);

    /** The people on the list who have not had this announcement, oldest first. */
    @Query(value = """
            select id from waitlist_entries
             where status = 'CONFIRMED' and (last_announcement is null or last_announcement <> :announcement)
             order by confirmed_at, id
            """, nativeQuery = true)
    List<UUID> findIdsToAnnounce(@Param("announcement") String announcement);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            update waitlist_entries set last_announcement = :announcement, last_announced_at = :now
             where id = :id
            """, nativeQuery = true)
    int markAnnounced(@Param("id") UUID id, @Param("announcement") String announcement, @Param("now") Instant now);
}
