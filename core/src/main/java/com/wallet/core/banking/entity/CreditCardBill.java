package com.wallet.core.banking.entity;

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
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "credit_card_bills")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreditCardBill {

    private @Id UUID id;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Column(name = "provider_bill_id", nullable = false)
    private String providerBillId;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(name = "closing_date")
    private LocalDate closingDate;

    @Column(name = "total_amount", nullable = false)
    private Money totalAmount;

    @Column(name = "minimum_payment")
    private Money minimumPayment;

    @Column(name = "currency_code", nullable = false)
    private String currencyCode;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
