package vn.com.pps.education.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;
import vn.com.pps.education.exception.InvalidGoogleTokenException;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * UC-01 Main Flow bước 4/A4 — xác thực Google id_token bằng JWKS công khai
 * của Google (KHÔNG dùng flow authorization-code của
 * spring-boot-starter-oauth2-client — UC-01 nhận id_token đã ký sẵn từ
 * Frontend, không có redirect/consent screen phía server).
 */
@Component
public class NimbusGoogleIdTokenVerifier implements GoogleIdTokenVerifier {

    private static final Set<String> VALID_ISSUERS = Set.of("accounts.google.com", "https://accounts.google.com");

    private final JwtDecoder jwtDecoder;

    public NimbusGoogleIdTokenVerifier(
            @Value("${app.security.google.jwks-uri}") String jwksUri,
            @Value("${app.security.google.client-ids}") String clientIdsCsv) {
        Set<String> allowedAudiences = Arrays.stream(clientIdsCsv.split(","))
                .map(String::trim)
                .filter(id -> !id.isBlank())
                .collect(Collectors.toSet());

        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwksUri).build();
        List<OAuth2TokenValidator<Jwt>> validators = List.of(
                new JwtTimestampValidator(),
                issuerValidator(),
                audienceValidator(allowedAudiences),
                emailVerifiedValidator()
        );
        decoder.setJwtValidator(JwtValidators.createDefaultWithValidators(validators));
        this.jwtDecoder = decoder;
    }

    @Override
    public GoogleIdentity verify(String idToken) {
        try {
            Jwt jwt = jwtDecoder.decode(idToken);
            String email = jwt.getClaimAsString("email");
            return new GoogleIdentity(jwt.getSubject(), email);
        } catch (JwtException ex) {
            throw new InvalidGoogleTokenException("Google id_token không hợp lệ.", ex);
        }
    }

    private OAuth2TokenValidator<Jwt> issuerValidator() {
        return jwt -> VALID_ISSUERS.contains(jwt.getIssuer() == null ? null : jwt.getIssuer().toString())
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(invalidIssuer());
    }

    private OAuth2TokenValidator<Jwt> audienceValidator(Set<String> allowedAudiences) {
        return jwt -> jwt.getAudience() != null && jwt.getAudience().stream().anyMatch(allowedAudiences::contains)
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(invalidAudience());
    }

    private OAuth2TokenValidator<Jwt> emailVerifiedValidator() {
        return jwt -> Boolean.TRUE.equals(jwt.getClaim("email_verified"))
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(invalidEmail());
    }

    private OAuth2Error invalidIssuer() {
        return new OAuth2Error("invalid_issuer", "Issuer không phải Google.", null);
    }

    private OAuth2Error invalidAudience() {
        return new OAuth2Error("invalid_audience", "Audience không khớp client-id đã cấu hình.", null);
    }

    private OAuth2Error invalidEmail() {
        return new OAuth2Error("email_not_verified", "Email Google chưa được xác minh.", null);
    }
}
