package com.wallet.core.waitlist.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/** One address on the landing page's waitlist, from the sign-up to the opening announcement. */
@Entity
@Table(name = "waitlist_entries")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WaitlistEntry {

    private @Id UUID id;

    /** Always stored trimmed and lower case. */
    @Column(nullable = false)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private WaitlistStatus status;

    /** Which text the person agreed to; the server sets it. */
    @Column(name = "consent_version", nullable = false)
    private String consentVersion;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    /** SHA-256 of the link's token. Kept after the confirmation: opening the link twice is not an error. */
    @Column(name = "confirm_token_hash")
    private String confirmTokenHash;

    @Column(name = "confirm_token_expires_at")
    private Instant confirmTokenExpiresAt;

    @Column(name = "confirm_sent_at")
    private Instant confirmSentAt;

    /** Born with the confirmation; in clear because every announcement rebuilds the link from it. */
    @Column(name = "unsubscribe_token")
    private String unsubscribeToken;

    @Column(name = "last_announcement")
    private String lastAnnouncement;

    @Column(name = "last_announced_at")
    private Instant lastAnnouncedAt;
}
