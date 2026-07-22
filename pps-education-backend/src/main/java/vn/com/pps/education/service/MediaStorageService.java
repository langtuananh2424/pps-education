package vn.com.pps.education.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.UUID;

/**
 * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng: lưu file audio/ảnh do
 * Giáo viên/Admin upload cho ngân hàng câu hỏi (UC-40, Question.audioUrl/
 * imageUrl) lên Cloudflare R2 (Object Storage tương thích S3 API) - thay
 * thế quyết định "lưu đĩa cục bộ + Docker volume" trước đó (2026-07-21) vì
 * Railway/production không có ổ đĩa bền vững đáng tin cậy như R2
 * (2026-07-22). Key R2 chia theo "thư mục" `{module}/audio|images/` (tiền
 * tố trong key, R2/S3 không có khái niệm thư mục thật, chỉ hiển thị dạng
 * cây trên Dashboard) - `module` do caller khai báo (xem MediaModule) để
 * phân biệt module gọi API dùng chung này, `audio`/`images` theo
 * content-type. Stateless: không có bảng DB nào theo dõi upload, key R2
 * sinh bằng UUID là nguồn dữ liệu duy nhất.
 */
@Service
public class MediaStorageService {

    private static final long MAX_IMAGE_BYTES = 10L * 1024 * 1024;
    private static final long MAX_AUDIO_BYTES = 50L * 1024 * 1024;

    private final S3Client r2Client;
    private final String bucket;
    private final String publicBaseUrl;

    public MediaStorageService(S3Client r2Client,
                                @Value("${app.media.r2.bucket}") String bucket,
                                @Value("${app.media.r2.public-base-url}") String publicBaseUrl) {
        this.r2Client = r2Client;
        this.bucket = bucket;
        this.publicBaseUrl = publicBaseUrl.endsWith("/")
                ? publicBaseUrl.substring(0, publicBaseUrl.length() - 1)
                : publicBaseUrl;
    }

    /** Validate module/loại/kích thước file, upload lên Cloudflare R2, trả về URL công khai. */
    public String store(MultipartFile file, String moduleCode) {
        MediaModule module = MediaModule.fromCode(moduleCode);
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File không được để trống.");
        }
        String contentType = file.getContentType();
        if (contentType == null) {
            throw new IllegalArgumentException("Không xác định được loại tệp (Content-Type).");
        }
        if (contentType.startsWith("audio/")) {
            if (file.getSize() > MAX_AUDIO_BYTES) {
                throw new IllegalArgumentException("Tệp âm thanh vượt quá dung lượng tối đa 50MB.");
            }
        } else if (contentType.startsWith("image/")) {
            if (file.getSize() > MAX_IMAGE_BYTES) {
                throw new IllegalArgumentException("Tệp ảnh vượt quá dung lượng tối đa 10MB.");
            }
        } else {
            throw new IllegalArgumentException("Chỉ chấp nhận tệp audio/* hoặc image/*, nhận được: " + contentType);
        }

        String category = contentType.startsWith("audio/") ? "audio" : "images";
        String key = module.folderPrefix() + "/" + category + "/" + UUID.randomUUID() + extensionOf(file.getOriginalFilename());
        try {
            r2Client.putObject(
                    PutObjectRequest.builder()
                            .bucket(bucket)
                            .key(key)
                            .contentType(contentType)
                            .contentLength(file.getSize())
                            .build(),
                    RequestBody.fromInputStream(file.getInputStream(), file.getSize()));
        } catch (IOException ex) {
            throw new UncheckedIOException("Không thể upload file lên R2.", ex);
        }
        return publicBaseUrl + "/" + key;
    }

    /** Chỉ giữ lại phần mở rộng gồm chữ/số (chặn path traversal/ký tự lạ từ tên file gốc). */
    private String extensionOf(String originalFilename) {
        String ext = StringUtils.getFilenameExtension(originalFilename);
        if (ext == null) {
            return "";
        }
        String sanitized = ext.replaceAll("[^a-zA-Z0-9]", "");
        if (sanitized.isEmpty() || sanitized.length() > 10) {
            return "";
        }
        return "." + sanitized.toLowerCase();
    }
}
