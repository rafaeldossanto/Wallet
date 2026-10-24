package com.wallet.core.waitlist.service;

import com.wallet.core.mail.EmailAddresses;
import com.wallet.core.waitlist.config.WaitlistProperties;
import com.wallet.core.waitlist.entity.WaitlistEntry;
import com.wallet.core.waitlist.entity.WaitlistStatus;
import com.wallet.core.waitlist.repository.WaitlistRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The opening announcement: one e-mail to everyone confirmed on the list who has not had this
 * announcement yet, at {@code ratePerSecond}, each with the recipient's own unsubscribe link.
 *
 * <p>Each e-mail is marked as sent only after the server took it, so running the command again
 * resends nothing, and an e-mail that failed is tried again then. No transaction and no database
 * connection is held while an e-mail goes out: every database call here is a short one of its own.
 */
@Slf4j
@Service
public class WaitlistAnnouncer {

    private final WaitlistRepository repository;
    private final WaitlistMailer mailer;
    private final WaitlistProperties properties;
    private final Clock clock;
    private final TransactionTemplate transactions;

    public WaitlistAnnouncer(WaitlistRepository repository, WaitlistMailer mailer, WaitlistProperties properties,
                             Clock clock, PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.mailer = mailer;
        this.properties = properties;
        this.clock = clock;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    public Totals announce(String announcementId, AnnouncementMessage message) {
        List<UUID> ids = repository.findIdsToAnnounce(announcementId);
        Duration pause = Duration.ofNanos(Duration.ofSeconds(1).toNanos() / Math.max(1, properties.announce().ratePerSecond()));
        int sent = 0;
        int failed = 0;
        for (UUID id : ids) {
            // Read again: the person may have left, or been told by another run, since the list was read.
            Optional<WaitlistEntry> entry = repository.findById(id).filter(found -> isDue(found, announcementId));
            if (entry.isEmpty()) {
                continue;
            }
            if (send(entry.get(), message, announcementId)) {
                sent++;
            } else {
                failed++;
            }
            if (!pause(pause)) {
                break;
            }
        }
        return new Totals(sent, failed);
    }

    private boolean send(WaitlistEntry entry, AnnouncementMessage message, String announcementId) {
        try {
            mailer.announce(entry.getEmail(), message, entry.getUnsubscribeToken());
        } catch (RuntimeException ex) {
            log.warn("[WAITLIST] Could not announce {} to entry {} ({}): {}", announcementId, entry.getId(),
                    EmailAddresses.mask(entry.getEmail()), ex.getMessage());
            return false;
        }
        transactions.executeWithoutResult(status -> repository.markAnnounced(entry.getId(), announcementId, clock.instant()));
        return true;
    }

    private static boolean isDue(WaitlistEntry entry, String announcementId) {
        return WaitlistStatus.CONFIRMED.equals(entry.getStatus()) && !announcementId.equals(entry.getLastAnnouncement());
    }

    /** @return false when the thread was interrupted: the run stops, with what it has sent so far counted */
    private static boolean pause(Duration duration) {
        try {
            Thread.sleep(duration);
            return true;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    public record Totals(int sent, int failed) {
    }
}
