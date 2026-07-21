package vn.com.pps.education.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng: lưu file audio/ảnh do
 * Giáo viên/Admin upload cho ngân hàng câu hỏi (UC-40, Question.audioUrl/
 * imageUrl) lên đĩa cục bộ (Docker volume bền vững - xem docker-compose.yml).
 * Stateless: không có bảng DB nào theo dõi upload, filesystem + tên file
 * UUID sinh ra là nguồn dữ liệu duy nhất.
 */
@Service
public class MediaStorageService {

    private static final long MAX_IMAGE_BYTES = 10L * 1024 * 1024;
    private static final long MAX_AUDIO_BYTES = 50L * 1024 * 1024;

    private final Path storageDir;

    public MediaStorageService(@Value("${app.media.storage-dir}") String storageDir) {
        this.storageDir = Path.of(storageDir).toAbsolutePath().normalize();
    }

    /** Validate loại/kích thước file, lưu xuống đĩa với tên UUID, trả về tên file đã lưu. */
    public String store(MultipartFile file) {
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

        String filename = UUID.randomUUID() + extensionOf(file.getOriginalFilename());
        try {
            Files.createDirectories(storageDir);
            file.transferTo(storageDir.resolve(filename));
        } catch (IOException ex) {
            throw new UncheckedIOException("Không thể lưu file upload.", ex);
        }
        return filename;
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
