package vn.com.pps.education.dto;

public record RefreshTokenResponse(
        String accessToken,
        String refreshToken,
        long accessTokenExpiresInSeconds
) {}
