package com.wallet.core.planning.repository;

import com.wallet.core.planning.entity.DismissedSuggestion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface DismissedSuggestionRepository extends JpaRepository<DismissedSuggestion, UUID> {

    List<DismissedSuggestion> findByUserId(UUID userId);

    /**
     * Two dismissals of the same suggestion at once (a double tap, a retry) keep one row: the unique
     * index decides with {@code ON CONFLICT DO NOTHING}, since a failed insert would poison the
     * transaction.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            INSERT INTO dismissed_fixed_expense_suggestions (id, user_id, description_key, day_of_month, created_at)
            VALUES (:id, :userId, :descriptionKey, :dayOfMonth, :createdAt)
            ON CONFLICT (user_id, description_key, day_of_month) DO NOTHING
            """, nativeQuery = true)
    void insertIfAbsent(@Param("id") UUID id, @Param("userId") UUID userId, @Param("descriptionKey") String descriptionKey,
                        @Param("dayOfMonth") int dayOfMonth, @Param("createdAt") Instant createdAt);
}
