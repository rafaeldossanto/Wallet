package com.wallet.core.banking.entity;

import com.wallet.core.shared.finance.AccountKind;
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
import java.util.UUID;

@Entity
@Table(name = "accounts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Account {

    private @Id UUID id;

    @Column(name = "connection_id", nullable = false)
    private UUID connectionId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "provider_account_id", nullable = false)
    private String providerAccountId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AccountKind kind;

    private String name;

    @Column(name = "number_last_digits")
    private String numberLastDigits;

    @Column(name = "currency_code", nullable = false)
    private String currencyCode;

    /** For a credit card: the amount owed on the open bill. */
    @Column(nullable = false)
    private Money balance;

    @Column(name = "credit_limit")
    private Money creditLimit;

    @Column(name = "available_credit")
    private Money availableCredit;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
