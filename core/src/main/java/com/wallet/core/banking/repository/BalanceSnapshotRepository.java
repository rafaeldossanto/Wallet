package com.wallet.core.banking.repository;

import com.wallet.core.banking.entity.BalanceSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface BalanceSnapshotRepository extends JpaRepository<BalanceSnapshot, BalanceSnapshot.Key> {

    List<BalanceSnapshot> findByAccountIdInOrderBySnapshotDateAscAccountIdAsc(Collection<UUID> accountIds);
}
