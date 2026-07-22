package vn.com.pps.education.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import vn.com.pps.education.dto.MediaUploadResponse;
import vn.com.pps.education.service.MediaStorageService;

/**
 * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng: API upload chung cho
 * file audio/ảnh, dùng trước tiên cho Question.audioUrl/imageUrl (UC-40).
 * Tham số bắt buộc "module" (xem MediaModule) để phân biệt "thư mục" trên
 * R2 theo module gọi lên - tránh trộn lẫn file khi module khác ngoài LMS
 * sau này cũng dùng API dùng chung này. Chỉ yêu cầu đã đăng nhập
 * (anyRequest().authenticated() ở SecurityConfig) - không gate permission
 * riêng, vì API ghi dữ liệu thật (VD POST /api/questions) đã tự gate qua
 * lms.exercise.manage.
 */
@RestController
public class MediaController {

    private final MediaStorageService mediaStorageService;

    public MediaController(MediaStorageService mediaStorageService) {
        this.mediaStorageService = mediaStorageService;
    }

    @PostMapping(value = "/api/media/upload", consumes = "multipart/form-data")
    public ResponseEntity<MediaUploadResponse> upload(@RequestParam("file") MultipartFile file,
                                                        @RequestParam("module") String module) {
        String url = mediaStorageService.store(file, module);
        return ResponseEntity.ok(new MediaUploadResponse(url));
    }
}
