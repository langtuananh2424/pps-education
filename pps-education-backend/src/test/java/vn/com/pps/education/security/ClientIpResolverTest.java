package vn.com.pps.education.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

/** Unit test thuần (không DB) — xem Javadoc ClientIpResolver. */
class ClientIpResolverTest {

    private final ClientIpResolver resolver = new ClientIpResolver();

    private static MockHttpServletRequest from(String remoteAddr) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(remoteAddr);
        return request;
    }

    @Test
    void resolve_prefersCfConnectingIpBehindDockerGateway() {
        MockHttpServletRequest request = from("172.28.0.1");
        request.addHeader("CF-Connecting-IP", "113.160.10.20");
        request.addHeader("X-Forwarded-For", "1.2.3.4, 127.0.0.1");

        assertThat(resolver.resolve(request)).isEqualTo("113.160.10.20");
    }

    @Test
    void resolve_fallsBackToRightmostPublicForwardedForHop() {
        MockHttpServletRequest request = from("127.0.0.1");
        request.addHeader("X-Forwarded-For", "8.8.8.8, 113.160.10.20, 127.0.0.1");

        assertThat(resolver.resolve(request)).isEqualTo("113.160.10.20");
    }

    @Test
    void resolve_ignoresHeadersFromUntrustedDirectClient() {
        MockHttpServletRequest request = from("113.160.10.20");
        request.addHeader("CF-Connecting-IP", "1.1.1.1");

        assertThat(resolver.resolve(request)).isEqualTo("113.160.10.20");
    }

    @Test
    void resolve_ignoresMalformedHeaderValues() {
        MockHttpServletRequest request = from("172.28.0.1");
        request.addHeader("CF-Connecting-IP", "999.1.1.1");
        request.addHeader("X-Forwarded-For", "evil.example.com");

        assertThat(resolver.resolve(request)).isEqualTo("172.28.0.1");
    }

    @Test
    void resolve_acceptsIpv6CfConnectingIp() {
        MockHttpServletRequest request = from("172.28.0.1");
        request.addHeader("CF-Connecting-IP", "2402:800:6314:1234::1");

        assertThat(resolver.resolve(request)).isEqualTo("2402:800:6314:1234::1");
    }

    @Test
    void resolve_returnsRemoteAddrWhenNoProxyHeaders() {
        assertThat(resolver.resolve(from("127.0.0.1"))).isEqualTo("127.0.0.1");
    }
}
