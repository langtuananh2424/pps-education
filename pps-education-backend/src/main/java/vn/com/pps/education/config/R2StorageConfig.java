package vn.com.pps.education.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

import java.net.URI;

/**
 * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng (2026-07-22): Cloudflare
 * R2 tương thích S3 API nên dùng chung AWS SDK S3 client, chỉ trỏ lại
 * endpoint theo Account ID R2 (thay vì vùng AWS thật). Region cố định
 * "auto" theo quy ước riêng của Cloudflare (R2 không có khái niệm vùng AWS).
 * Thay thế MediaWebConfig cũ (phục vụ file tĩnh từ đĩa cục bộ).
 */
@Configuration
public class R2StorageConfig {

    @Bean
    public S3Client r2Client(@Value("${app.media.r2.account-id}") String accountId,
                              @Value("${app.media.r2.access-key}") String accessKey,
                              @Value("${app.media.r2.secret-key}") String secretKey) {
        return S3Client.builder()
                .endpointOverride(URI.create("https://" + accountId + ".r2.cloudflarestorage.com"))
                .region(Region.of("auto"))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKey, secretKey)))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(true)
                        .build())
                .httpClient(UrlConnectionHttpClient.create())
                .build();
    }
}
