package com.wallet.core.waitlist.mapper;

import com.wallet.core.waitlist.entity.WaitlistEntry;
import com.wallet.core.waitlist.entity.WaitlistStatus;
import lombok.experimental.UtilityClass;

import java.time.Instant;
import java.util.UUID;

@UtilityClass
public class WaitlistMapper {

    /** A sign-up whose confirmation link (its hash) goes out now. */
    public WaitlistEntry toPending(String email, String consentVersion, String confirmTokenHash, Instant now,
                                   Instant confirmTokenExpiresAt) {
        return WaitlistEntry.builder()
                .id(UUID.randomUUID())
                .email(email)
                .status(WaitlistStatus.PENDING)
                .consentVersion(consentVersion)
                .createdAt(now)
                .confirmTokenHash(confirmTokenHash)
                .confirmTokenExpiresAt(confirmTokenExpiresAt)
                .confirmSentAt(now)
                .build();
    }
}
