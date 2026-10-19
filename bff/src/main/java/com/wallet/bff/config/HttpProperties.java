package com.wallet.bff.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.unit.DataSize;

/**
 * @param maxBodySize the largest request body accepted. Every body the app sends is a small JSON
 *                    (login, a fixed expense); 256 KB is far beyond any of them. Same default as the core
 */
@ConfigurationProperties("wallet.http")
public record HttpProperties(@DefaultValue("256KB") DataSize maxBodySize) {
}
