package vn.com.pps.education.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import vn.com.pps.education.dto.ReviewVideoCatalogImportResponse;
import vn.com.pps.education.security.AuthenticatedUser;
import vn.com.pps.education.service.ReviewVideoCatalogImportService;

/**
 * UC-73: Import Excel hàng loạt "bộ" video ôn tập vào Kho Video Ôn tập
 * (FR-LMS-01). Xem docs/uc/phan-he-07-lms-portal.md (UC-73). Đòi hỏi đủ 2
 * quyền tương ứng thao tác tay ở Kho Video (tạo Bộ dùng
 * lms.review-video.create, thêm Video vào Bộ dùng lms.review-video.update
 * — xem ReviewVideoController#createSet/addVideo).
 */
@RestController
@PreAuthorize("hasPermission(null, 'lms.review-video.create') and hasPermission(null, 'lms.review-video.update')")
public class ReviewVideoCatalogImportController {

    private final ReviewVideoCatalogImportService reviewVideoCatalogImportService;

    public ReviewVideoCatalogImportController(ReviewVideoCatalogImportService reviewVideoCatalogImportService) {
        this.reviewVideoCatalogImportService = reviewVideoCatalogImportService;
    }

    @PostMapping(value = "/api/review-video-sets/imports", consumes = "multipart/form-data")
    public ResponseEntity<ReviewVideoCatalogImportResponse> importCatalog(@RequestParam("file") MultipartFile file,
                                                                            @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(reviewVideoCatalogImportService.importCatalog(file, actor.userId()));
    }

    @GetMapping("/api/review-video-sets/imports/{id}")
    public ResponseEntity<ReviewVideoCatalogImportResponse> getJob(@PathVariable Long id) {
        return ResponseEntity.ok(reviewVideoCatalogImportService.getJob(id));
    }
}
