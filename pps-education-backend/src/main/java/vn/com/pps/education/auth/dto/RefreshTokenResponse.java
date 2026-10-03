package vn.com.pps.education.auth.dto;

public record RefreshTokenResponse(
        String accessToken,
        String refreshToken,
        long accessTokenExpiresInSeconds
) {}
