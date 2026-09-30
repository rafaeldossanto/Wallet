package com.wallet.core.banking.repository;

import com.wallet.core.banking.entity.CreditCardBill;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CreditCardBillRepository extends JpaRepository<CreditCardBill, UUID> {

    List<CreditCardBill> findByAccountId(UUID accountId);

    List<CreditCardBill> findByAccountIdOrderByDueDateDesc(UUID accountId);
}
