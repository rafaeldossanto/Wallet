package com.wallet.core.provider.pluggy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Pluggy's JSON, trimmed to the fields the Wallet uses. Shapes follow the official SDK types
 * (github.com/pluggyai/pluggy-node, src/types). Nothing here leaves the {@code pluggy} package.
 *
 * <p>Numbers are {@link BigDecimal} so Jackson reads the exact digits Pluggy sent.
 */
final class PluggyDtos {

    private PluggyDtos() {
    }

    record AuthRequest(String clientId, String clientSecret, boolean nonExpiring) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record AuthResponse(String apiKey) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Page<T>(List<T> results, Integer page, Integer total, Integer totalPages) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record CursorPage<T>(List<T> results, String next) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Connector(Integer id, String name, String imageUrl) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Item(String id, Connector connector, String status, String executionStatus,
                Instant lastUpdatedAt, Instant consentExpiresAt, String clientUserId) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record CreditData(BigDecimal creditLimit, BigDecimal availableCreditLimit, Instant balanceDueDate) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Account(String id, String itemId, String type, String subtype, String number, String name,
                   String marketingName, BigDecimal balance, String currencyCode, CreditData creditData) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record CreditCardMetadata(Integer installmentNumber, Integer totalInstallments, String billId) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Transaction(String id, String accountId, Instant date, String description, String type,
                       BigDecimal amount, String currencyCode, String category, String status,
                       String providerId, CreditCardMetadata creditCardMetadata) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Bill(String id, Instant dueDate, Instant billClosingDate, BigDecimal totalAmount,
                String totalAmountCurrencyCode, BigDecimal minimumPaymentAmount) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Investment(String id, String itemId, String type, String subtype, String name, String currencyCode,
                      BigDecimal balance, BigDecimal amountOriginal, Instant dueDate, String status) {
    }
}
