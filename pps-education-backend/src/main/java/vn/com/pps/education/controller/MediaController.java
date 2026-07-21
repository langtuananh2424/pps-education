package vn.com.pps.education.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import vn.com.pps.education.dto.MediaUploadResponse;
import vn.com.pps.education.service.MediaStorageService;

/**
 * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng: API upload chung cho
 * file audio/ảnh, dùng trước tiên cho Question.audioUrl/imageUrl (UC-40).
 * Chỉ yêu cầu đã đăng nhập (anyRequest().authenticated() ở SecurityConfig) -
 * không gate permission riêng, vì API ghi dữ liệu thật (VD POST /api/questions)
 * đã tự gate qua lms.exercise.manage.
 */
@RestController
public class MediaController {

    private final MediaStorageService mediaStorageService;

    public MediaController(MediaStorageService mediaStorageService) {
        this.mediaStorageService = mediaStorageService;
    }

    @PostMapping(value = "/api/media/upload", consumes = "multipart/form-data")
    public ResponseEntity<MediaUploadResponse> upload(@RequestParam("file") MultipartFile file,
                                                        HttpServletRequest request) {
        String filename = mediaStorageService.store(file);
        String url = ServletUriComponentsBuilder.fromContextPath(request)
                .path("/media-files/")
                .path(filename)
                .toUriString();
        return ResponseEntity.ok(new MediaUploadResponse(url));
    }
}
