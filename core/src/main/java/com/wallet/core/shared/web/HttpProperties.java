package com.wallet.core.shared.web;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.unit.DataSize;

/**
 * @param maxBodySize the largest request body accepted. Every body the core takes is a small JSON
 *                    (login, a fixed expense); 256 KB is far beyond any of them
 */
@ConfigurationProperties("wallet.http")
public record HttpProperties(@DefaultValue("256KB") DataSize maxBodySize) {
}
