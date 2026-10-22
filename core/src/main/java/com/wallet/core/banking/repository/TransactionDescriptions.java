package com.wallet.core.banking.repository;

import com.wallet.core.banking.entity.AccountTransaction;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * What the text search reads: only the id and the description of each transaction the filter
 * keeps, newest first. The description is encrypted, so the match happens in memory; reading two
 * columns instead of whole entities keeps that to a decryption per row, and only the page found
 * is then loaded in full.
 */
@Repository
@RequiredArgsConstructor
public class TransactionDescriptions {

    private final EntityManager entityManager;

    /** At most {@code limit} rows, in the statement's order. The description comes decrypted (or null). */
    public List<Row> of(TransactionFilter filter, int limit) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Row> query = cb.createQuery(Row.class);
        Root<AccountTransaction> root = query.from(AccountTransaction.class);
        query.select(cb.construct(Row.class, root.get("id"), root.get("description")))
                .where(filter.toSpecification().toPredicate(root, query, cb))
                .orderBy(cb.desc(root.get("bookedOn")), cb.desc(root.get("createdAt")), cb.asc(root.get("id")));
        return entityManager.createQuery(query).setMaxResults(limit).getResultList();
    }

    public record Row(UUID id, String description) {
    }
}
