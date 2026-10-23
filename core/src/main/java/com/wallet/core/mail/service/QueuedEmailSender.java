package com.wallet.core.mail.service;

import com.wallet.core.mail.EmailSender;
import com.wallet.core.mail.OutgoingEmail;
import com.wallet.core.mail.transport.MailTransport;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * After the caller's commit, on a virtual thread of Spring Boot's application executor (see
 * {@link EmailSender} for why). Logs carry the kind of e-mail and nothing else: the address is
 * personal data and the text may hold a live link.
 */
@Slf4j
@Service
public class QueuedEmailSender implements EmailSender {

    private final MailTransport transport;
    private final TaskExecutor executor;

    public QueuedEmailSender(MailTransport transport, @Qualifier("applicationTaskExecutor") TaskExecutor executor) {
        this.transport = transport;
        this.executor = executor;
    }

    @Override
    public void send(OutgoingEmail email) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    dispatch(email);
                }
            });
        } else {
            dispatch(email);
        }
    }

    private void dispatch(OutgoingEmail email) {
        executor.execute(() -> {
            try {
                transport.deliver(email);
                log.info("[MAIL] Sent a {} e-mail", email.kind());
            } catch (RuntimeException ex) {
                log.warn("[MAIL] Could not send a {} e-mail: {}", email.kind(), ex.getMessage());
            }
        });
    }
}
