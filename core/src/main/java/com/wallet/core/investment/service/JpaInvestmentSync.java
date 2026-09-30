package com.wallet.core.investment.service;

import com.wallet.core.investment.InvestmentSync;
import com.wallet.core.investment.entity.Investment;
import com.wallet.core.investment.entity.InvestmentSnapshot;
import com.wallet.core.investment.mapper.InvestmentSyncMapper;
import com.wallet.core.investment.repository.InvestmentRepository;
import com.wallet.core.investment.repository.InvestmentSnapshotRepository;
import com.wallet.core.provider.ProviderInvestment;
import com.wallet.core.shared.money.Money;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.util.Objects.isNull;

@Service
@RequiredArgsConstructor
class JpaInvestmentSync implements InvestmentSync {

    private final InvestmentRepository repository;
    private final InvestmentSnapshotRepository snapshots;
    private final Clock clock;

    @Override
    @Transactional
    public int apply(UUID connectionId, UUID userId, LocalDate today, List<ProviderInvestment> sources) {
        Instant now = clock.instant();
        Map<String, Investment> local = repository.findByConnectionId(connectionId).stream()
                .collect(Collectors.toMap(Investment::getProviderInvestmentId, Function.identity()));

        int open = 0;
        for (ProviderInvestment source : sources) {
            Investment target = local.remove(source.id());
            if (isNull(target)) {
                target = repository.save(InvestmentSyncMapper.newInvestment(connectionId, userId, source, now));
            } else {
                InvestmentSyncMapper.copy(source, target, now);
            }
            snapshots.save(new InvestmentSnapshot(target.getId(), today, isNull(target.getClosedAt()) ? target.getBalance() : Money.ZERO));
            if (source.active()) {
                open++;
            }
        }
        // Positions closed on an earlier sync already have their zero snapshot.
        local.values().stream()
                .filter(missing -> isNull(missing.getClosedAt()))
                .forEach(missing -> {
                    missing.setClosedAt(now);
                    missing.setUpdatedAt(now);
                    snapshots.save(new InvestmentSnapshot(missing.getId(), today, Money.ZERO));
                });
        return open;
    }
}
