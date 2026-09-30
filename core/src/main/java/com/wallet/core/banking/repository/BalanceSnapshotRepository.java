package com.wallet.core.banking.repository;

import com.wallet.core.banking.entity.BalanceSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BalanceSnapshotRepository extends JpaRepository<BalanceSnapshot, BalanceSnapshot.Key> {
}
