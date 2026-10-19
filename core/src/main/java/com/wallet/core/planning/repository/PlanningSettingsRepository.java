package com.wallet.core.planning.repository;

import com.wallet.core.planning.entity.PlanningSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public interface PlanningSettingsRepository extends JpaRepository<PlanningSettings, UUID> {

    /**
     * Creates the user's row on the first adjustment, updates it after. Two first saves at once (a
     * double tap, a retry) would both insert and the second fail on the primary key, so the insert
     * uses {@code ON CONFLICT}: a failed insert would poison the transaction.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            INSERT INTO planning_settings (user_id, expected_salary, created_at, updated_at)
            VALUES (:userId, :expectedSalary, :now, :now)
            ON CONFLICT (user_id) DO UPDATE
               SET expected_salary = EXCLUDED.expected_salary, updated_at = EXCLUDED.updated_at
            """, nativeQuery = true)
    void saveExpectedSalary(@Param("userId") UUID userId, @Param("expectedSalary") BigDecimal expectedSalary,
                            @Param("now") Instant now);
}
