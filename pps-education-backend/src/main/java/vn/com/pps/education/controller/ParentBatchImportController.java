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
import vn.com.pps.education.dto.ParentBatchImportResponse;
import vn.com.pps.education.security.AuthenticatedUser;
import vn.com.pps.education.service.ParentBatchImportService;

/**
 * UC-50: Nhập phụ huynh theo lô, liên kết học sinh có sẵn (FR-STU-04) —
 * xem Javadoc ParentBatchImportService. Dùng permission student.parent.manage
 * (tách từ student.manage ở V44, khớp StudentController.linkParent — UC-13)
 * vì UC-50 cũng tạo hồ sơ phụ huynh, chỉ khác là hàng loạt qua Excel.
 *
 * @PreAuthorize đặt ở class-level ngay từ đầu — rút kinh nghiệm lỗ hổng
 * thiếu quyền từng xảy ra ở StudentBatchImportController.
 */
@RestController
@PreAuthorize("hasPermission(null, 'student.parent.manage')")
public class ParentBatchImportController {

    private final ParentBatchImportService parentBatchImportService;

    public ParentBatchImportController(ParentBatchImportService parentBatchImportService) {
        this.parentBatchImportService = parentBatchImportService;
    }

    @PostMapping(value = "/api/parent-imports", consumes = "multipart/form-data")
    public ResponseEntity<ParentBatchImportResponse> importParents(@RequestParam("file") MultipartFile file,
                                                                       @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(parentBatchImportService.importParents(file, actor.userId()));
    }

    @GetMapping("/api/parent-imports/{id}")
    public ResponseEntity<ParentBatchImportResponse> getJob(@PathVariable Long id) {
        return ResponseEntity.ok(parentBatchImportService.getJob(id));
    }
}
