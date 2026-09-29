package vn.com.pps.education.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.util.regex.Pattern;

/**
 * Xác định IP thật của client đứng sau chuỗi proxy production:
 * Trình duyệt → Cloudflare → cloudflared (Tunnel) → Nginx (127.0.0.1) → container backend.
 * {@link HttpServletRequest#getRemoteAddr()} ở backend luôn là gateway mạng Docker (VD 172.28.0.1)
 * nên mọi bản ghi login_attempts/refresh_tokens trước đây đều mang cùng 1 IP, đồng thời
 * LoginIpThrottle đếm chung toàn hệ thống như thể 1 IP.
 *
 * Chỉ tin header chuyển tiếp khi kết nối trực tiếp tới từ proxy nội bộ (loopback/dải private) — không
 * ai ngoài server gọi thẳng được backend (cổng bind 127.0.0.1), còn Cloudflare luôn ghi đè
 * CF-Connecting-IP ở edge nên client không tự giả mạo được. Thứ tự ưu tiên:
 * CF-Connecting-IP → IP public ngoài cùng bên phải trong X-Forwarded-For → remoteAddr.
 */
@Component
public class ClientIpResolver {

    static final String CF_CONNECTING_IP = "CF-Connecting-IP";
    static final String X_FORWARDED_FOR = "X-Forwarded-For";

    /** Chỉ chấp nhận chuỗi dạng IP literal — InetAddress.getByName với tên miền sẽ tra DNS, cột INET cũng từ chối chuỗi rác. */
    private static final Pattern IPV4 = Pattern.compile("^(\\d{1,3})(\\.\\d{1,3}){3}$");
    private static final Pattern IPV6 = Pattern.compile("^[0-9a-fA-F:.]+$");

    public String resolve(HttpServletRequest request) {
        String remoteAddr = request.getRemoteAddr();
        if (!isTrustedProxy(remoteAddr)) {
            return remoteAddr;
        }

        String cfIp = normalize(request.getHeader(CF_CONNECTING_IP));
        if (cfIp != null) {
            return cfIp;
        }

        String forwardedFor = request.getHeader(X_FORWARDED_FOR);
        if (forwardedFor != null) {
            String[] hops = forwardedFor.split(",");
            for (int i = hops.length - 1; i >= 0; i--) {
                String hop = normalize(hops[i]);
                if (hop != null && !isTrustedProxy(hop)) {
                    return hop;
                }
            }
        }
        return remoteAddr;
    }

    private static boolean isTrustedProxy(String ip) {
        InetAddress address = parse(ip);
        return address != null
                && (address.isLoopbackAddress() || address.isSiteLocalAddress() || address.isLinkLocalAddress()
                || isUniqueLocalIpv6(address));
    }

    private static boolean isUniqueLocalIpv6(InetAddress address) {
        byte[] bytes = address.getAddress();
        return bytes.length == 16 && (bytes[0] & 0xFE) == 0xFC; // fc00::/7
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return parse(trimmed) == null ? null : trimmed;
    }

    /** Octet &gt; 255 (VD 999.1.1.1) không phải literal hợp lệ — getByName sẽ coi là tên miền và tra DNS. */
    private static boolean isValidIpv4(String value) {
        if (!IPV4.matcher(value).matches()) {
            return false;
        }
        for (String octet : value.split("\\.")) {
            if (Integer.parseInt(octet) > 255) {
                return false;
            }
        }
        return true;
    }

    private static InetAddress parse(String value) {
        if (value == null || value.isEmpty() || value.length() > 45) {
            return null;
        }
        boolean looksLikeIp = isValidIpv4(value) || (value.contains(":") && IPV6.matcher(value).matches());
        if (!looksLikeIp) {
            return null;
        }
        try {
            return InetAddress.getByName(value);
        } catch (Exception e) {
            return null;
        }
    }
}
