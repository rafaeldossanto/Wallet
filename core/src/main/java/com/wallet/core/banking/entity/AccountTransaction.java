package com.wallet.core.banking.entity;

import com.wallet.core.shared.crypto.EncryptedStringConverter;
import com.wallet.core.shared.finance.Direction;
import com.wallet.core.shared.finance.TransactionStatus;
import com.wallet.core.shared.money.Money;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
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

/** A transaction of an account ("Transaction" alone would clash with the transaction annotations). */
@Entity
@Table(name = "transactions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AccountTransaction {

    private @Id UUID id;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "provider_transaction_id", nullable = false)
    private String providerTransactionId;

    @Column(name = "provider_reconcile_id")
    private String providerReconcileId;

    @Column(name = "booked_on", nullable = false)
    private LocalDate bookedOn;

    /** Encrypted at rest: a database dump does not reveal where the user spends money. */
    @Convert(converter = EncryptedStringConverter.class)
    private String description;

    /** Always positive; {@link #direction} says in or out. */
    @Column(nullable = false)
    private Money amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Direction direction;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionStatus status;

    private String category;

    @Column(name = "installment_number")
    private Integer installmentNumber;

    @Column(name = "installment_total")
    private Integer installmentTotal;

    @Column(name = "provider_bill_id")
    private String providerBillId;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
