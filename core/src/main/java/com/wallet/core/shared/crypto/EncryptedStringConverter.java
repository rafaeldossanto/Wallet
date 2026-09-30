package com.wallet.core.shared.crypto;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.stereotype.Component;

/**
 * Opt-in per column with {@code @Convert(converter = EncryptedStringConverter.class)}.
 * A Spring bean: Spring Boot hands Hibernate a bean container, so the key gets injected.
 */
@Component
@Converter
public class EncryptedStringConverter implements AttributeConverter<String, String> {

    private final DataCipher cipher;

    public EncryptedStringConverter(DataCipher cipher) {
        this.cipher = cipher;
    }

    @Override
    public String convertToDatabaseColumn(String attribute) {
        return cipher.encrypt(attribute);
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        return cipher.decrypt(dbData);
    }
}
