package vn.com.pps.education.media.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.net.URI;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;

/**
 * Rà soát bảo mật 2026-09-28 (đã xác nhận với người dùng): file cá nhân (ảnh đại diện, bài nộp của Học
 * sinh, mẫu/báo cáo đã sinh - xem {@link MediaModule#privateFiles()}) không còn đọc công khai trên bucket.
 * DB vẫn lưu URL gốc dạng {publicBaseUrl}/{key}; lúc trả JSON cho client, URL gốc được đổi thành URL có
 * chữ ký S3 (SigV4 presigned GET), hết hạn sau {@code app.media.r2.signed-url-ttl-minutes}. Khi client gửi
 * ngược URL đã ký lên (VD lưu lại form hồ sơ), {@link #toCanonical} đổi về URL gốc trước khi vào Service -
 * xem SignedMediaUrlJacksonConfig.
 *
 * {@code app.media.r2.signed-url-endpoint} là gốc URL công khai mà trình duyệt gọi được và chuyển tiếp
 * NGUYÊN path + Host tới storage (VD https://files.ppsvietnam.edu.vn, Nginx proxy /pps-media/* thẳng vào
 * MinIO - xem deploy/nginx/files.conf.template). Để trống thì tắt ký (URL trả về giữ nguyên) - dùng cho máy
 * dev/test, nơi bucket vẫn đọc công khai.
 */
@Service
public class MediaUrlSigner {

    private static final String SIGNATURE_PARAM = "X-Amz-Signature=";

    private final String publicBaseUrl;
    private final String bucket;
    private final Duration ttl;
    private final List<String> privatePrefixes;
    private final String signedUrlPrefix;
    private final S3Presigner presigner;

    public MediaUrlSigner(@Value("${app.media.r2.public-base-url}") String publicBaseUrl,
                          @Value("${app.media.r2.bucket}") String bucket,
                          @Value("${app.media.r2.access-key}") String accessKey,
                          @Value("${app.media.r2.secret-key}") String secretKey,
                          @Value("${app.media.r2.signed-url-endpoint:}") String signedUrlEndpoint,
                          @Value("${app.media.r2.signed-url-ttl-minutes:60}") long ttlMinutes) {
        this.publicBaseUrl = stripTrailingSlash(publicBaseUrl);
        this.bucket = bucket;
        this.ttl = Duration.ofMinutes(ttlMinutes);
        this.privatePrefixes = Arrays.stream(MediaModule.values())
                .filter(MediaModule::privateFiles)
                .map(m -> m.folderPrefix() + "/")
                .toList();
        String endpoint = stripTrailingSlash(signedUrlEndpoint == null ? "" : signedUrlEndpoint.trim());
        if (endpoint.isEmpty()) {
            this.signedUrlPrefix = null;
            this.presigner = null;
        } else {
            this.signedUrlPrefix = endpoint + "/" + bucket + "/";
            this.presigner = S3Presigner.builder()
                    .endpointOverride(URI.create(endpoint))
                    .region(Region.of("auto"))
                    .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)))
                    .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                    .build();
        }
    }

    /** true nếu đang bật ký URL (đã cấu hình signed-url-endpoint). */
    public boolean enabled() {
        return presigner != null;
    }

    /**
     * Đổi URL gốc của file cá nhân thành URL có chữ ký, có hạn. Chuỗi khác (URL nội dung giảng dạy công
     * khai, URL ngoài hệ thống, văn bản thường) trả nguyên.
     */
    public String sign(String value) {
        if (presigner == null || value == null || !value.startsWith(publicBaseUrl + "/")) {
            return value;
        }
        String key = value.substring(publicBaseUrl.length() + 1);
        if (!isPrivateKey(key)) {
            return value;
        }
        return presigner.presignGetObject(GetObjectPresignRequest.builder()
                        .signatureDuration(ttl)
                        .getObjectRequest(GetObjectRequest.builder().bucket(bucket).key(key).build())
                        .build())
                .url()
                .toString();
    }

    /**
     * Ngược lại {@link #sign}: URL đã ký do chính hệ thống cấp -> URL gốc {publicBaseUrl}/{key} để lưu DB.
     * Chữ ký KHÔNG được kiểm tra ở đây (không cần - URL gốc tự nó không cấp quyền đọc gì).
     */
    public String toCanonical(String value) {
        if (signedUrlPrefix == null || value == null || !value.startsWith(signedUrlPrefix)
                || !value.contains(SIGNATURE_PARAM)) {
            return value;
        }
        int queryStart = value.indexOf('?');
        if (queryStart < 0) {
            return value;
        }
        String encodedKey = value.substring(signedUrlPrefix.length(), queryStart);
        String key = URI.create("/" + encodedKey).getPath().substring(1);
        return publicBaseUrl + "/" + key;
    }

    private boolean isPrivateKey(String key) {
        for (String prefix : privatePrefixes) {
            if (key.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private static String stripTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
