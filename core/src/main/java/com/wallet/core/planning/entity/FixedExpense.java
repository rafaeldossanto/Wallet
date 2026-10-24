package com.wallet.core.planning.entity;

import com.wallet.core.shared.crypto.LegacyAwareEncryptedStringConverter;
import com.wallet.core.shared.money.Money;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
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

/** An expense that is not on a card and repeats every month: tuition, rent, a boleto. */
@Entity
@Table(name = "fixed_expenses")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FixedExpense {

    private @Id UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /**
     * Encrypted at rest: the name is free text and can say a lot ("Psicólogo"). Rows from before the
     * encryption read as they are until the start-up backfill encrypts them.
     */
    @Convert(converter = LegacyAwareEncryptedStringConverter.class)
    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private Money amount;

    /** 1 to 31; in a shorter month it falls on the last day. */
    @Column(name = "day_of_month", nullable = false)
    private int dayOfMonth;

    private String category;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
