package vn.com.pps.education.dto;

/** UC-01 bước 5: cấp Access Token + Refresh Token khi xác thực thành công. */
public record LoginResponse(
        String accessToken,
        String refreshToken,
        long accessTokenExpiresInSeconds
) {}
