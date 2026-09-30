package com.wallet.core.investment.repository;

import com.wallet.core.investment.entity.InvestmentSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InvestmentSnapshotRepository extends JpaRepository<InvestmentSnapshot, InvestmentSnapshot.Key> {
}
