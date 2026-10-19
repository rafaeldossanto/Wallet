package com.wallet.core.connection.dto;

/**
 * @param connectToken opens the Pluggy Connect widget for this user; valid for 30 minutes and
 *                     meant for a single connection
 */
public record ConnectTokenResponse(String connectToken) {
}
