package com.wallet.core.mail.service;

import com.wallet.core.mail.OutgoingEmail;
import com.wallet.core.mail.transport.MailDeliveryException;
import com.wallet.core.mail.transport.MailTransport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class QueuedEmailSenderTest {

    private static final OutgoingEmail EMAIL = OutgoingEmail.of("rafael@example.com", "Assunto", "Texto", "test");

    private final List<OutgoingEmail> delivered = new ArrayList<>();
    private final QueuedEmailSender sender = new QueuedEmailSender(delivered::add, new SyncTaskExecutor());

    @AfterEach
    void endTransaction() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    /** A link must never reach an inbox for a token that was rolled back. */
    @Test
    void insideATransactionItWaitsForTheCommit() {
        TransactionSynchronizationManager.initSynchronization();

        sender.send(EMAIL);

        assertThat(delivered).isEmpty();
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        assertThat(delivered).containsExactly(EMAIL);
    }

    @Test
    void aRollbackSendsNothing() {
        TransactionSynchronizationManager.initSynchronization();

        sender.send(EMAIL);
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(synchronization -> synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

        assertThat(delivered).isEmpty();
    }

    @Test
    void outsideATransactionItGoesRightAway() {
        sender.send(EMAIL);

        assertThat(delivered).containsExactly(EMAIL);
    }

    /** The caller's work is done by then: a server that refuses is logged, not thrown at it. */
    @Test
    void aDeliveryFailureDoesNotReachTheCaller() {
        MailTransport refusing = email -> {
            throw new MailDeliveryException("SMTP did not take the test e-mail", new RuntimeException("rafael@example.com"));
        };

        assertThatCode(() -> new QueuedEmailSender(refusing, new SyncTaskExecutor()).send(EMAIL)).doesNotThrowAnyException();
    }
}
