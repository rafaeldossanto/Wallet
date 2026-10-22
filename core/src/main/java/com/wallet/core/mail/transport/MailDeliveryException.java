package com.wallet.core.mail.transport;

/** The message names only the kind of e-mail: the cause may carry the address. */
public class MailDeliveryException extends RuntimeException {

    public MailDeliveryException(String message, Throwable cause) {
        super(message, cause);
    }
}
