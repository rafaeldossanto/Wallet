package com.wallet.core.banking.repository;

import com.wallet.core.banking.entity.Account;
import com.wallet.core.banking.entity.AccountTransaction;
import com.wallet.core.shared.finance.AccountKind;
import com.wallet.core.shared.finance.Direction;
import com.wallet.core.shared.time.DateRange;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static java.util.Objects.nonNull;

/**
 * Statement filters. Text search is not here: the description is encrypted, so it is matched in
 * memory after decryption (see {@code BankingReadService}).
 *
 * @param spendingOnly only what counts as spending, by the same rule as the spending insights:
 *                     outflows, without a bank account's card bill payments (the purchases are
 *                     already on the card)
 */
public record TransactionFilter(UUID userId, DateRange period, UUID accountId, Direction direction, boolean spendingOnly) {

    public TransactionFilter(UUID userId, DateRange period, UUID accountId, Direction direction) {
        this(userId, period, accountId, direction, false);
    }

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
            if (spendingOnly) {
                Subquery<UUID> cards = query.subquery(UUID.class);
                Root<Account> card = cards.from(Account.class);
                cards.select(card.get("id")).where(cb.equal(card.get("kind"), AccountKind.CREDIT_CARD));
                predicates.add(cb.equal(root.get("direction"), Direction.OUTFLOW));
                predicates.add(cb.or(
                        root.get("accountId").in(cards),
                        cb.isNull(root.get("category")),
                        cb.notEqual(cb.lower(root.get("category")), BankingAggregates.CARD_PAYMENT_CATEGORY)));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
