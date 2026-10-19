package com.wallet.core.provider.pluggy;

import lombok.experimental.UtilityClass;
import org.springframework.http.HttpMethod;

import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Names each Pluggy endpoint for the audit trail. The names are the Wallet's, not Pluggy's, so
 * another provider's adapter would record the same operations. An endpoint missing here is still
 * recorded, named after its method and fixed path segments ({@code GET /connectors/201} is
 * {@code GET_CONNECTORS}); ids never make it into the name.
 */
@UtilityClass
class PluggyOperations {

    static final String AUTH = "AUTH";
    static final String CREATE_CONNECT_TOKEN = "CREATE_CONNECT_TOKEN";
    static final String FIND_ITEM = "FIND_ITEM";
    static final String DELETE_ITEM = "DELETE_ITEM";
    static final String LIST_ACCOUNTS = "LIST_ACCOUNTS";
    static final String LIST_TRANSACTIONS = "LIST_TRANSACTIONS";
    static final String LIST_BILLS = "LIST_BILLS";
    static final String LIST_INVESTMENTS = "LIST_INVESTMENTS";

    /** Resource names and API versions; any other segment is an id (UUIDs, numbers, {@code demo-nubank}). */
    private static final Pattern FIXED_SEGMENT = Pattern.compile("[a-z_]+|v\\d+");
    private static final String ID = "{id}";
    /** Size of {@code provider_calls.operation}. */
    private static final int MAX_LENGTH = 40;

    String of(HttpMethod method, URI uri) {
        List<String> segments = template(uri.getPath());
        return switch (method.name() + " /" + String.join("/", segments)) {
            case "POST /auth" -> AUTH;
            case "POST /connect_token" -> CREATE_CONNECT_TOKEN;
            case "GET /items/{id}" -> FIND_ITEM;
            case "DELETE /items/{id}" -> DELETE_ITEM;
            case "GET /accounts" -> LIST_ACCOUNTS;
            case "GET /transactions", "GET /v2/transactions" -> LIST_TRANSACTIONS;
            case "GET /bills" -> LIST_BILLS;
            case "GET /investments" -> LIST_INVESTMENTS;
            default -> unnamed(method, segments);
        };
    }

    private static List<String> template(String path) {
        return Arrays.stream(path.split("/"))
                .filter(segment -> !segment.isEmpty())
                .map(segment -> FIXED_SEGMENT.matcher(segment).matches() ? segment : ID)
                .toList();
    }

    private static String unnamed(HttpMethod method, List<String> segments) {
        String name = segments.stream()
                .filter(segment -> !ID.equals(segment))
                .map(segment -> segment.toUpperCase(Locale.ROOT))
                .collect(Collectors.joining("_", method.name() + "_", ""));
        return name.length() <= MAX_LENGTH ? name : name.substring(0, MAX_LENGTH);
    }
}
