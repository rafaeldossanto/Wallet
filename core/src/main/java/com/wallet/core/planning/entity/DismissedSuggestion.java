package com.wallet.core.planning.entity;

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

/** A suggested fixed expense the user said no to: it is not suggested again. */
@Entity
@Table(name = "dismissed_fixed_expense_suggestions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DismissedSuggestion {

    private @Id UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /** A keyed fingerprint of the debits' description words, never the description. */
    @Column(name = "description_key", nullable = false)
    private String descriptionKey;

    /** The debit's usual day when it was dismissed; a few days around it count too. */
    @Column(name = "day_of_month", nullable = false)
    private int dayOfMonth;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
