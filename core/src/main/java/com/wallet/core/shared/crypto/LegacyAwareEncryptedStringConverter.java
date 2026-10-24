package com.wallet.core.shared.crypto;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.stereotype.Component;

/**
 * For a column that held plain text before it was encrypted (L7): what is written is always
 * encrypted, and what is read without the version prefix is a row from before, which comes back as
 * it is. {@code FixedExpenseNameBackfill} encrypts those rows at start-up, and from then on every
 * value carries the prefix. Opt-in per column with
 * {@code @Convert(converter = LegacyAwareEncryptedStringConverter.class)}.
 *
 * <p>Unlike {@link EncryptedStringConverter}, where a value without the prefix is unreadable and
 * comes back as {@code null}: a column that never held plain text should stay strict.
 */
@Component
@Converter
public class LegacyAwareEncryptedStringConverter implements AttributeConverter<String, String> {

    private final DataCipher cipher;

    public LegacyAwareEncryptedStringConverter(DataCipher cipher) {
        this.cipher = cipher;
    }

    @Override
    public String convertToDatabaseColumn(String attribute) {
        return cipher.encrypt(attribute);
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        return cipher.isEncrypted(dbData) ? cipher.decrypt(dbData) : dbData;
    }
}
