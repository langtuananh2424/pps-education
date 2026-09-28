package vn.com.pps.education.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
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
 * cầu FE; tách 2 cờ độc lập 2026-07-27): module có `acceptsVideo()=true`
 * (CURRICULUM_DOCUMENT, REVIEW_VIDEO, LMS_QUESTION) được nhận thêm video/*
 * (≤200MB); module có `acceptsOfficeDocuments()=true` (CURRICULUM_DOCUMENT,
 * LMS_QUESTION — KHÔNG có REVIEW_VIDEO, Kho Video Ôn tập chỉ nhận video/
 * audio) được nhận thêm PDF/Word/Excel/PowerPoint (≤20MB). Cả 2 độc lập
 * với audio/ảnh (luôn được phép mọi module).
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
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            // UC-67: mẫu báo cáo dạng .html (REPORT_TEMPLATE) - đã xác nhận với người dùng (2026-08-09).
            "text/html");

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
        } else if (module.acceptsVideo() && contentType.startsWith("video/")) {
            category = "video";
            maxBytes = MAX_VIDEO_BYTES;
        } else if (module.acceptsOfficeDocuments() && DOCUMENT_CONTENT_TYPES.contains(contentType)) {
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

    /**
     * UC-68: tải lại nội dung 1 file đã upload trước đó qua {@link #store}
     * (VD file mẫu report_templates.file_url) để xử lý phía server (mail
     * merge) — bổ sung ngoài SDD gốc, đã xác nhận với người dùng
     * (2026-08-09). Chỉ chấp nhận URL thuộc đúng publicBaseUrl của storage
     * này (suy ngược lại R2 object key từ URL công khai).
     */
    public byte[] download(String publicUrl) {
        return downloadWithContentType(publicUrl).bytes();
    }

    /** File tải về kèm content-type THẬT đã lưu lúc upload (khác {@link #download}, chỉ trả bytes). */
    public record DownloadedFile(byte[] bytes, String contentType) {
    }

    /**
     * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-25 — fix bug thật: caller trước đây phải
     * tự đoán/hardcode content-type (VD ReflexSequentialGradingService hardcode "audio/webm" cho MỌI
     * audio ghi âm, dù MediaRecorder trên Safari/iOS thực ra ghi ra "audio/mp4") vì {@link #download} chỉ
     * trả bytes, bỏ mất content-type THẬT đã lưu ở R2 lúc upload (xem {@code file.getContentType()} ở
     * {@link #store}). Method này đọc lại đúng content-type đó — caller cần biết định dạng thật (VD gửi
     * cho AI đa phương thức) dùng hàm này thay vì {@link #download}.
     */
    public DownloadedFile downloadWithContentType(String publicUrl) {
        String key = objectKeyOf(publicUrl);
        try (var stream = r2Client.getObject(GetObjectRequest.builder().bucket(bucket).key(key).build())) {
            return new DownloadedFile(stream.readAllBytes(), stream.response().contentType());
        } catch (IOException ex) {
            throw new UncheckedIOException("Không tải được file từ storage (key=" + key + ").", ex);
        }
    }

    /**
     * Validate URL media do người dùng gửi lên (VD audioUrl khi nộp bài) TRƯỚC khi lưu vào DB - chỉ
     * nhận URL do chính hệ thống sinh ra qua {@link #store}. Ném IllegalArgumentException (400) nếu không.
     */
    public void requireStoredUrl(String publicUrl) {
        objectKeyOf(publicUrl);
    }

    /**
     * Chống SSRF (rà soát bảo mật 2026-09-28, đã xác nhận với người dùng): URL truyền vào có thể đến
     * từ request của người dùng (VD audioUrl học sinh gửi khi nộp bài Video phản xạ - UC-23b), nên
     * TUYỆT ĐỐI không mở kết nối tới địa chỉ trong URL. Chỉ chấp nhận URL do chính {@link #store}/
     * {@link #storeGeneratedFile} sinh ra ({publicBaseUrl}/{key}), suy ngược ra object key rồi đọc qua
     * S3 client - server không bao giờ tự đi tới host/scheme (http, file...) do người dùng chọn.
     * Trước đây có nhánh fallback tải qua URLConnection + trả HTML mẫu giả lập khi lỗi - đã bỏ.
     */
    private String objectKeyOf(String publicUrl) {
        if (publicUrl == null || !publicUrl.startsWith(publicBaseUrl + "/")) {
            throw new IllegalArgumentException("URL file không thuộc kho lưu trữ của hệ thống.");
        }
        String key = publicUrl.substring(publicBaseUrl.length() + 1);
        if (key.isBlank() || key.startsWith("/") || key.contains("..") || key.contains("\\")
                || key.contains("?") || key.contains("#")) {
            throw new IllegalArgumentException("URL file không hợp lệ.");
        }
        return key;
    }

    /**
     * UC-68: upload file do SERVER tự sinh ra (docx đã mail-merge, zip gộp
     * nhiều docx) — khác {@link #store} (dành cho file người dùng tải lên
     * qua HTTP, cần validate loại/kích thước). Bổ sung ngoài SDD gốc, đã
     * xác nhận với người dùng (2026-08-09).
     */
    public String storeGeneratedFile(byte[] data, String filename, String contentType, MediaModule module) {
        String key = module.folderPrefix() + "/generated/" + UUID.randomUUID() + extensionOf(filename);
        r2Client.putObject(
                PutObjectRequest.builder()
                        .bucket(bucket)
                        .key(key)
                        .contentType(contentType)
                        .contentLength((long) data.length)
                        .build(),
                RequestBody.fromBytes(data));
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
