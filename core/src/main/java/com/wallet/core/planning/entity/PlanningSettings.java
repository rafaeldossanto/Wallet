package com.wallet.core.planning.entity;

import com.wallet.core.shared.money.Money;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/** What the user adjusted in the plan; one row per user, created on the first adjustment. */
@Entity
@Table(name = "planning_settings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlanningSettings {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    /** Replaces the prediction while the month's salary has not landed; null = predict from the previous month. */
    @Column(name = "expected_salary")
    private Money expectedSalary;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
