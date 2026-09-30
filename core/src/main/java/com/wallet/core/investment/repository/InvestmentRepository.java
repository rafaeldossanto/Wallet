package com.wallet.core.investment.repository;

import com.wallet.core.investment.entity.Investment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface InvestmentRepository extends JpaRepository<Investment, UUID> {

    List<Investment> findByConnectionId(UUID connectionId);

    List<Investment> findByUserIdAndClosedAtIsNullOrderByKindAscNameAsc(UUID userId);
}
