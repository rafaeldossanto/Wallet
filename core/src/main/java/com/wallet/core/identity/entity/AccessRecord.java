package com.wallet.core.identity.entity;

import com.wallet.core.shared.crypto.EncryptedStringConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
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

/**
 * A record of access to the application (Marco Civil da Internet, art. 15): who, from which
 * address, when. Kept 6 months, in secrecy, and read only by a court order, straight in the
 * database: no route reads it. {@code userId} is not a foreign key, so the record outlives the
 * account (LGPD, art. 16, I).
 */
@Entity
@Table(name = "access_records")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AccessRecord {

    private @Id UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AccessEvent event;

    /** Encrypted at rest: a database dump does not say who connected from where. */
    @Convert(converter = EncryptedStringConverter.class)
    @Column(nullable = false)
    private String ip;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;
}
