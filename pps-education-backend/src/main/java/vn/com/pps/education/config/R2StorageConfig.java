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
 *
 * Bổ sung `endpoint-url` (2026-08-26, đã xác nhận với người dùng): server
 * production tự host (không có static IP, dùng Cloudflare Tunnel) chuyển
 * sang lưu file bằng MinIO tự host thay vì Cloudflare R2 thật, để tránh phụ
 * thuộc dịch vụ ngoài. MinIO tương thích S3 API giống R2 nên dùng lại
 * NGUYÊN VẸN S3Client/MediaStorageService này — chỉ cần trỏ endpoint khác.
 * Nếu `app.media.r2.endpoint-url` được set (VD `http://minio:9000` trong
 * network Docker nội bộ) thì dùng thẳng URL đó; để trống thì giữ hành vi cũ
 * (tự dựng URL R2 từ account-id) — không phá vỡ cấu hình R2 thật đang dùng
 * ở môi trường khác.
 */
@Configuration
public class R2StorageConfig {

    @Bean
    public S3Client r2Client(@Value("${app.media.r2.account-id}") String accountId,
                              @Value("${app.media.r2.access-key}") String accessKey,
                              @Value("${app.media.r2.secret-key}") String secretKey,
                              @Value("${app.media.r2.endpoint-url:}") String endpointUrl) {
        String resolvedEndpoint = endpointUrl.isBlank()
                ? "https://" + accountId + ".r2.cloudflarestorage.com"
                : endpointUrl;
        return S3Client.builder()
                .endpointOverride(URI.create(resolvedEndpoint))
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
