package com.wallet.core;

import com.wallet.core.mail.OutgoingEmail;
import com.wallet.core.mail.transport.MailTransport;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.fail;

/**
 * Keeps every e-mail instead of sending it. Sending happens after the commit on another thread, so
 * the tests wait for a message to arrive instead of expecting it right away.
 */
public class RecordingMailTransport implements MailTransport {

    private static final Pattern TOKEN = Pattern.compile("#token=([A-Za-z0-9_-]+)");
    private static final Duration WAIT = Duration.ofSeconds(5);

    private final List<OutgoingEmail> sent = new CopyOnWriteArrayList<>();

    @Override
    public void deliver(OutgoingEmail email) {
        sent.add(email);
    }

    public List<OutgoingEmail> to(String address) {
        return sent.stream().filter(email -> email.to().equals(address)).toList();
    }

    /** Waits for the {@code count}-th e-mail to this address and returns it. */
    public OutgoingEmail awaitNth(String address, int count) throws InterruptedException {
        long deadline = System.nanoTime() + WAIT.toNanos();
        while (System.nanoTime() < deadline) {
            List<OutgoingEmail> received = to(address);
            if (received.size() >= count) {
                return received.get(count - 1);
            }
            Thread.sleep(20);
        }
        return fail("No e-mail number %d to %s within %s; got %s", count, address, WAIT, to(address));
    }

    public OutgoingEmail await(String address) throws InterruptedException {
        return awaitNth(address, 1);
    }

    /** The token in the confirmation link of this e-mail. */
    public static String tokenIn(OutgoingEmail email) {
        Matcher matcher = TOKEN.matcher(email.text());
        if (!matcher.find()) {
            return fail("No link in %s", email.subject());
        }
        return matcher.group(1);
    }

    /** Nothing more to this address, given the queue a moment to drain. */
    public void assertNothingMoreTo(String address, int expected) throws InterruptedException {
        Thread.sleep(300);
        if (to(address).size() != expected) {
            fail("Expected %d e-mails to %s, got %s", expected, address, to(address));
        }
    }
}
