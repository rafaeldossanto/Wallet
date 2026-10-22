package com.wallet.core.mail;

import lombok.experimental.UtilityClass;

/** E-mail addresses as people may see them where the full address does not belong. */
@UtilityClass
public class EmailAddresses {

    /** {@code rafael@example.com} becomes {@code r*****@example.com}: enough to recognize, not to copy. */
    public String mask(String address) {
        int at = address.lastIndexOf('@');
        if (at <= 0) {
            return "***";
        }
        return address.charAt(0) + "*".repeat(Math.max(3, at - 1)) + address.substring(at);
    }
}
