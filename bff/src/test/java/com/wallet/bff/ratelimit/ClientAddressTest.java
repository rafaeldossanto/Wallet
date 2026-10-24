package com.wallet.bff.ratelimit;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.List;

import static java.util.Objects.nonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClientAddressTest {

    private static final ClientAddress BEHIND_PROXY = new ClientAddress(List.of("10.0.0.0/8", "192.0.2.10", "fd00::/8"));

    @Test
    void withoutTrustedProxiesTheHeaderIsIgnored() {
        ClientAddress direct = new ClientAddress(List.of());

        assertThat(direct.of(request("198.51.100.4", "203.0.113.7"))).isEqualTo("198.51.100.4");
    }

    @Test
    void aConnectionFromOutsideTheTrustedProxiesCannotClaimAnotherAddress() {
        assertThat(BEHIND_PROXY.of(request("198.51.100.4", "203.0.113.7"))).isEqualTo("198.51.100.4");
    }

    @Test
    void behindATrustedProxyTheAddressItSawIsTheClient() {
        assertThat(BEHIND_PROXY.of(request("10.0.0.5", "203.0.113.7"))).isEqualTo("203.0.113.7");
    }

    /** nginx with proxy_add_x_forwarded_for appends to what the client sent: only the right end is ours. */
    @Test
    void whatTheClientPutOnTheLeftIsIgnored() {
        assertThat(BEHIND_PROXY.of(request("10.0.0.5", "1.2.3.4, 5.6.7.8, 203.0.113.7"))).isEqualTo("203.0.113.7");
    }

    @Test
    void severalTrustedProxiesAreSkippedFromTheRight() {
        assertThat(BEHIND_PROXY.of(request("10.0.0.5", "1.2.3.4, 203.0.113.7, 192.0.2.10, 10.1.2.3")))
                .isEqualTo("203.0.113.7");
    }

    @Test
    void severalHeadersAreReadInOrder() {
        MockHttpServletRequest request = request("10.0.0.5", "1.2.3.4");
        request.addHeader(ClientAddress.FORWARDED_FOR, "203.0.113.7");

        assertThat(BEHIND_PROXY.of(request)).isEqualTo("203.0.113.7");
    }

    @Test
    void withoutTheHeaderTheProxyItselfIsCounted() {
        assertThat(BEHIND_PROXY.of(request("10.0.0.5", null))).isEqualTo("10.0.0.5");
    }

    @Test
    void somethingThatIsNotAnAddressIsCountedOnTheProxy() {
        assertThat(BEHIND_PROXY.of(request("10.0.0.5", "1.2.3.4, evil.example.com"))).isEqualTo("10.0.0.5");
    }

    @Test
    void ipv6RangesWork() {
        assertThat(BEHIND_PROXY.of(request("fd12::1", "2001:db8::7"))).isEqualTo("2001:db8:0:0:0:0:0:7");
    }

    @Test
    void anInvalidTrustedProxyFailsAtStartup() {
        assertThatThrownBy(() -> new ClientAddress(List.of("proxy.internal")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ClientAddress(List.of("10.0.0.0/33")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rangesMatchOnPartialBytes() {
        ClientAddress range = new ClientAddress(List.of("172.16.0.0/12"));

        assertThat(range.of(request("172.31.255.1", "203.0.113.7"))).isEqualTo("203.0.113.7");
        assertThat(range.of(request("172.32.0.1", "203.0.113.7"))).isEqualTo("172.32.0.1");
    }

    private static MockHttpServletRequest request(String remoteAddress, String forwardedFor) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(remoteAddress);
        if (nonNull(forwardedFor)) {
            request.addHeader(ClientAddress.FORWARDED_FOR, forwardedFor);
        }
        return request;
    }

    /** The list of addresses a public route lets in (the provider's webhooks): IPs and CIDR ranges, v4 and v6. */
    @Test
    void anAllowlistLetsInOnlyItsRangesAndAnEmptyOneEveryone() {
        ClientAddress.Allowlist list = new ClientAddress.Allowlist(List.of("198.51.100.0/24", "203.0.113.7", " ", "2001:db8::/32"));

        assertThat(list.allows("198.51.100.200")).isTrue();
        assertThat(list.allows("203.0.113.7")).isTrue();
        assertThat(list.allows("203.0.113.8")).isFalse();
        assertThat(list.allows("2001:db8:1::5")).isTrue();
        assertThat(list.allows("2001:db9::1")).isFalse();
        assertThat(list.allows("not-an-ip")).isFalse();
        assertThat(new ClientAddress.Allowlist(List.of()).allows("192.0.2.1")).isTrue();
        assertThat(new ClientAddress.Allowlist(List.of()).isEmpty()).isTrue();
        assertThatThrownBy(() -> new ClientAddress.Allowlist(List.of("garbage"))).isInstanceOf(IllegalArgumentException.class);
    }
}
