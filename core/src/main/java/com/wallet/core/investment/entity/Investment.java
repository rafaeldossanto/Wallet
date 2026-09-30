package com.wallet.core.investment.entity;

import com.wallet.core.shared.finance.InvestmentKind;
import com.wallet.core.shared.money.Money;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "investments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Investment {

    private @Id UUID id;

    @Column(name = "connection_id", nullable = false)
    private UUID connectionId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "provider_investment_id", nullable = false)
    private String providerInvestmentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InvestmentKind kind;

    private String subtype;

    private String name;

    @Column(name = "currency_code", nullable = false)
    private String currencyCode;

    @Column(nullable = false)
    private Money balance;

    @Column(name = "amount_invested")
    private Money amountInvested;

    @Column(name = "due_date")
    private LocalDate dueDate;

    /** Fully withdrawn, or no longer reported by the provider. Closed positions leave the totals. */
    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
