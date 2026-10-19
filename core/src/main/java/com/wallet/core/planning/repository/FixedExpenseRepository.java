package com.wallet.core.planning.repository;

import com.wallet.core.planning.entity.FixedExpense;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FixedExpenseRepository extends JpaRepository<FixedExpense, UUID> {

    List<FixedExpense> findByUserIdOrderByDayOfMonthAscNameAsc(UUID userId);

    Optional<FixedExpense> findByIdAndUserId(UUID id, UUID userId);

    long countByUserId(UUID userId);
}
