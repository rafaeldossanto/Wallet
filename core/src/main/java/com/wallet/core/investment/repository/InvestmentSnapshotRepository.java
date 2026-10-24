package com.wallet.core.investment.repository;

import com.wallet.core.investment.entity.InvestmentSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface InvestmentSnapshotRepository extends JpaRepository<InvestmentSnapshot, InvestmentSnapshot.Key> {

    List<InvestmentSnapshot> findByInvestmentIdInOrderBySnapshotDateAscInvestmentIdAsc(Collection<UUID> investmentIds);
}
