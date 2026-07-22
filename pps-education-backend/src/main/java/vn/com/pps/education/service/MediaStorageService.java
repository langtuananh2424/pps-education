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
import java.util.Set;
import java.util.UUID;

/**
 * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng: lưu file audio/ảnh do
 * Giáo viên/Admin upload cho ngân hàng câu hỏi (UC-40, Question.audioUrl/
 * imageUrl) lên Cloudflare R2 (Object Storage tương thích S3 API) - thay
 * thế quyết định "lưu đĩa cục bộ + Docker volume" trước đó (2026-07-21) vì
 * Railway/production không có ổ đĩa bền vững đáng tin cậy như R2
 * (2026-07-22). Key R2 chia theo "thư mục" `{module}/{category}/` (tiền tố
 * trong key, R2/S3 không có khái niệm thư mục thật, chỉ hiển thị dạng cây
 * trên Dashboard) - `module` do caller khai báo (xem MediaModule) để phân
 * biệt module gọi API dùng chung này, `category` theo content-type
 * (audio/images/video/documents). Stateless: không có bảng DB nào theo dõi
 * upload, key R2 sinh bằng UUID là nguồn dữ liệu duy nhất.
 *
 * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng (2026-07-22, theo yêu
 * cầu FE): module có `acceptsDocuments()=true` (CURRICULUM_DOCUMENT,
 * LESSON_MATERIAL) được nhận thêm PDF/Word/Excel/PowerPoint (≤20MB) và
 * video/* (≤200MB) ngoài audio/ảnh - LMS_QUESTION giữ nguyên hành vi cũ.
 */
@Service
public class MediaStorageService {

    private static final long MAX_IMAGE_BYTES = 10L * 1024 * 1024;
    private static final long MAX_AUDIO_BYTES = 50L * 1024 * 1024;
    private static final long MAX_DOCUMENT_BYTES = 20L * 1024 * 1024;
    private static final long MAX_VIDEO_BYTES = 200L * 1024 * 1024;

    private static final Set<String> DOCUMENT_CONTENT_TYPES = Set.of(
            "application/pdf",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.ms-powerpoint",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation");

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
        String category;
        long maxBytes;
        if (contentType.startsWith("audio/")) {
            category = "audio";
            maxBytes = MAX_AUDIO_BYTES;
        } else if (contentType.startsWith("image/")) {
            category = "images";
            maxBytes = MAX_IMAGE_BYTES;
        } else if (module.acceptsDocuments() && contentType.startsWith("video/")) {
            category = "video";
            maxBytes = MAX_VIDEO_BYTES;
        } else if (module.acceptsDocuments() && DOCUMENT_CONTENT_TYPES.contains(contentType)) {
            category = "documents";
            maxBytes = MAX_DOCUMENT_BYTES;
        } else {
            throw new IllegalArgumentException("Loại tệp không được hỗ trợ cho module " + module + ": " + contentType);
        }
        if (file.getSize() > maxBytes) {
            throw new IllegalArgumentException("Tệp vượt quá dung lượng tối đa cho phép ("
                    + (maxBytes / (1024 * 1024)) + "MB).");
        }

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
