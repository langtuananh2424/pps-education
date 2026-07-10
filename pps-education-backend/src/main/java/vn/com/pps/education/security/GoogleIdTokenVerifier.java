package vn.com.pps.education.security;

import vn.com.pps.education.exception.InvalidGoogleTokenException;

/**
 * Xác thực Google id_token (chữ ký JWKS, issuer, audience, email_verified).
 * Tách interface để AuthService không phụ thuộc trực tiếp thư viện Nimbus,
 * và để test có thể giả lập thay vì gọi mạng thật tới Google.
 */
public interface GoogleIdTokenVerifier {

    /** @throws InvalidGoogleTokenException nếu token sai chữ ký/hết hạn/sai audience/email chưa xác minh. */
    GoogleIdentity verify(String idToken);
}
