package com.wallet.bff.ratelimit;

import jakarta.servlet.http.HttpServletRequest;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static java.util.Objects.isNull;
import static org.springframework.util.StringUtils.hasText;

/**
 * The address a request really came from, for the rate limit.
 *
 * <p>{@code X-Forwarded-For} is written by whoever sends the request, so it is believed only when
 * the connection itself comes from a trusted proxy. Even then only the hops the trusted proxies
 * appended count: the list is read from the right, skipping trusted proxies, and the first address
 * that is not one of them is the client. Anything to its left was sent by the client and is
 * ignored. Without trusted proxies the connection's address is used, whatever the header says.
 */
public class ClientAddress {

    static final String FORWARDED_FOR = "X-Forwarded-For";

    private final List<AddressRange> trustedProxies;

    /** @param trustedProxies IPs ({@code 192.0.2.10}) or CIDR ranges ({@code 10.0.0.0/8}, {@code fd00::/8}) */
    public ClientAddress(List<String> trustedProxies) {
        this.trustedProxies = trustedProxies.stream().filter(proxy -> hasText(proxy)).map(AddressRange::parse).toList();
    }

    public String of(HttpServletRequest request) {
        String remote = request.getRemoteAddr();
        if (trustedProxies.isEmpty() || !isTrusted(remote)) {
            return remote;
        }
        List<String> hops = forwardedHops(request);
        for (int index = hops.size() - 1; index >= 0; index--) {
            String hop = hops.get(index);
            Optional<InetAddress> address = literal(hop);
            if (address.isEmpty()) {
                // A trusted proxy would only append addresses: something is off, count it on the proxy.
                return remote;
            }
            if (!isTrusted(address.get())) {
                return address.get().getHostAddress();
            }
        }
        // Every hop is one of ours: the request started inside the trusted network.
        return hops.isEmpty() ? remote : hops.getFirst();
    }

    private boolean isTrusted(String address) {
        return literal(address).map(this::isTrusted).orElse(false);
    }

    private boolean isTrusted(InetAddress address) {
        return trustedProxies.stream().anyMatch(range -> range.contains(address));
    }

    /** Every {@code X-Forwarded-For} header, in order, split at the commas. */
    private static List<String> forwardedHops(HttpServletRequest request) {
        List<String> hops = new ArrayList<>();
        for (String header : Collections.list(request.getHeaders(FORWARDED_FOR))) {
            for (String hop : header.split(",")) {
                if (hasText(hop)) {
                    hops.add(hop.strip());
                }
            }
        }
        return hops;
    }

    /** Parses only IP literals: a host name would mean a DNS lookup per request. */
    static Optional<InetAddress> literal(String text) {
        if (isNull(text)) {
            return Optional.empty();
        }
        try {
            return Optional.of(InetAddress.ofLiteral(text.strip()));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    /** One IP or a CIDR range of the same family. */
    record AddressRange(byte[] network, int prefixLength) {

        static AddressRange parse(String text) {
            String[] parts = text.strip().split("/", 2);
            InetAddress address = literal(parts[0])
                    .orElseThrow(() -> new IllegalArgumentException("Not an IP or CIDR range: " + text));
            byte[] bytes = address.getAddress();
            int prefix = parts.length == 1 ? bytes.length * 8 : Integer.parseInt(parts[1].strip());
            if (prefix < 0 || prefix > bytes.length * 8) {
                throw new IllegalArgumentException("Invalid prefix length in " + text);
            }
            return new AddressRange(bytes, prefix);
        }

        boolean contains(InetAddress address) {
            byte[] candidate = address.getAddress();
            if (candidate.length != network.length) {
                return false;
            }
            int fullBytes = prefixLength / 8;
            for (int index = 0; index < fullBytes; index++) {
                if (candidate[index] != network[index]) {
                    return false;
                }
            }
            int remainingBits = prefixLength % 8;
            if (remainingBits == 0) {
                return true;
            }
            int mask = 0xFF << (8 - remainingBits) & 0xFF;
            return (candidate[fullBytes] & mask) == (network[fullBytes] & mask);
        }
    }
}
