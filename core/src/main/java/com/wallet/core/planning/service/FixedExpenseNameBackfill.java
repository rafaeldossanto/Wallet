package com.wallet.core.planning.service;

import com.wallet.core.shared.crypto.DataCipher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;

import static java.util.Objects.requireNonNullElse;

/**
 * Encrypts the names of the fixed expenses written before the column was encrypted (L7). At
 * start-up, in batches, each in its own transaction; what already carries the {@code v1:} prefix is
 * left alone, so running it again changes nothing. Two instances starting together are safe: a row
 * is updated only if it still holds the plain text this one read, so the second finds nothing to do.
 *
 * <p>It reads and writes the column through SQL, not through the entity, whose converter would
 * hand the plain text back as if it were fine.
 */
@Slf4j
@Service
public class FixedExpenseNameBackfill {

    static final int BATCH = 100;

    private final JdbcTemplate jdbc;
    private final DataCipher cipher;
    private final TransactionTemplate transactions;

    public FixedExpenseNameBackfill(JdbcTemplate jdbc, DataCipher cipher, PlatformTransactionManager transactionManager) {
        this.jdbc = jdbc;
        this.cipher = cipher;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        run();
    }

    /** @return how many names it encrypted */
    public int run() {
        int total = 0;
        int encrypted;
        do {
            encrypted = requireNonNullElse(transactions.execute(status -> encryptBatch()), 0);
            total += encrypted;
        } while (encrypted > 0);
        if (total > 0) {
            log.info("[CRYPTO] Encrypted {} fixed expense names written in plain text", total);
        }
        return total;
    }

    /** One batch; 0 when no plain name is left (or another instance took the ones this one read). */
    private int encryptBatch() {
        List<PlainName> plain = jdbc.query(
                "SELECT id, name FROM fixed_expenses WHERE name NOT LIKE 'v1:%' ORDER BY id LIMIT ?",
                (rows, index) -> new PlainName(rows.getObject("id", UUID.class), rows.getString("name")), BATCH);
        int updated = 0;
        for (PlainName row : plain) {
            updated += jdbc.update("UPDATE fixed_expenses SET name = ? WHERE id = ? AND name = ?",
                    cipher.encrypt(row.name()), row.id(), row.name());
        }
        // A batch another instance won reads as zero here, and the loop ends: the next start-up looks again.
        return updated;
    }

    private record PlainName(UUID id, String name) {
    }
}
