package com.wallet.core.identity.service;

import com.wallet.core.identity.dto.RegisterRequest;
import com.wallet.core.identity.entity.EmailTokenPurpose;
import com.wallet.core.identity.entity.User;
import com.wallet.core.identity.mapper.UserMapper;
import com.wallet.core.identity.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;

/**
 * Sign-up with the same answer for every e-mail (finding A16): "we sent you a link". What happens
 * behind it depends on the e-mail, and only its owner finds out, in the inbox:
 *
 * <ul>
 *   <li>new: an unconfirmed account and a confirmation link;</li>
 *   <li>taken by a confirmed account: a notice to its owner, nothing else;</li>
 *   <li>taken by an unconfirmed account: the latest sign-up wins, with its password, and a new link
 *       (the older ones stop working). Keeping the first one would let a stranger sign up first with
 *       someone else's e-mail and wait for its owner to confirm an account with the stranger's
 *       password in it.</li>
 * </ul>
 *
 * <p>The password is hashed in every case and before any database work, so the answer takes as
 * long whatever the e-mail, and no connection is held while BCrypt waits for its turn.
 */
@Service
public class RegistrationService {

    private final UserRepository users;
    private final PasswordHasher hasher;
    private final EmailLinkTokens links;
    private final AccountMailer mailer;
    private final Clock clock;
    private final TransactionTemplate transactions;

    public RegistrationService(UserRepository users, PasswordHasher hasher, EmailLinkTokens links, AccountMailer mailer,
                               Clock clock, PlatformTransactionManager transactionManager) {
        this.users = users;
        this.hasher = hasher;
        this.links = links;
        this.mailer = mailer;
        this.clock = clock;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    public void register(RegisterRequest request) {
        String email = Emails.normalize(request.email());
        String passwordHash = hasher.encode(request.password());
        transactions.executeWithoutResult(status -> {
            Instant now = clock.instant();
            User candidate = UserMapper.toUnconfirmed(email, passwordHash, request.displayName(), now);
            if (users.insertUnconfirmedIfAbsent(candidate.getId(), email, passwordHash, candidate.getDisplayName(), now) == 1) {
                sendConfirmation(candidate);
                return;
            }
            users.findByEmailForUpdate(email).ifPresent(existing -> {
                if (existing.isEmailConfirmed()) {
                    mailer.signUpAttempt(existing);
                } else if (links.mayIssue(existing.getId(), EmailTokenPurpose.CONFIRM_ACCOUNT)) {
                    existing.setPasswordHash(passwordHash);
                    existing.setDisplayName(candidate.getDisplayName());
                    // The days until an unconfirmed account is deleted count from the latest sign-up.
                    existing.setCreatedAt(now);
                    sendConfirmation(existing);
                }
            });
        });
    }

    /** Another link for an unconfirmed account, within the hourly limit; for any other e-mail, nothing. Same answer either way. */
    public void resendConfirmation(String rawEmail) {
        transactions.executeWithoutResult(status -> users.findByEmailForUpdate(Emails.normalize(rawEmail))
                .ifPresent(this::resendIfUnconfirmed));
    }

    /**
     * The right password on an unconfirmed account: the sign-in still fails like a wrong one (else
     * a stranger who signed up with someone's e-mail would learn whether it had an account), and
     * the link goes again, in case the first one got lost.
     */
    public void resendIfUnconfirmed(User user) {
        if (!user.isEmailConfirmed() && links.mayIssue(user.getId(), EmailTokenPurpose.CONFIRM_ACCOUNT)) {
            sendConfirmation(user);
        }
    }

    private void sendConfirmation(User user) {
        mailer.confirmAccount(user, links.issue(user.getId(), EmailTokenPurpose.CONFIRM_ACCOUNT, null));
    }
}
