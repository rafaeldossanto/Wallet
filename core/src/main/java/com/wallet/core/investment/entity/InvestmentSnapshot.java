package com.wallet.core.investment.entity;

import com.wallet.core.shared.money.Money;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "investment_snapshots")
@IdClass(InvestmentSnapshot.Key.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class InvestmentSnapshot {

    @Id
    @Column(name = "investment_id")
    private UUID investmentId;

    @Id
    @Column(name = "snapshot_date")
    private LocalDate snapshotDate;

    @Column(nullable = false)
    private Money balance;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class Key implements Serializable {
        private UUID investmentId;
        private LocalDate snapshotDate;
    }
}
