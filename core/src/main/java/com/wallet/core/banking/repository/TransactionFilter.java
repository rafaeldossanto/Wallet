package com.wallet.core.banking.repository;

import com.wallet.core.banking.entity.AccountTransaction;
import com.wallet.core.shared.finance.Direction;
import com.wallet.core.shared.time.DateRange;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static java.util.Objects.nonNull;

/**
 * Statement filters. Text search is not here: the description is encrypted, so it is matched in
 * memory after decryption (see {@code BankingReadService}).
 */
public record TransactionFilter(UUID userId, DateRange period, UUID accountId, Direction direction) {

    public Specification<AccountTransaction> toSpecification() {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("userId"), userId));
            predicates.add(cb.isNull(root.get("deletedAt")));
            predicates.add(cb.between(root.get("bookedOn"), period.from(), period.to()));
            if (nonNull(accountId)) {
                predicates.add(cb.equal(root.get("accountId"), accountId));
            }
            if (nonNull(direction)) {
                predicates.add(cb.equal(root.get("direction"), direction));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
