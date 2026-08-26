package vn.com.pps.education.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.com.pps.education.dto.SubTopicResponse;
import vn.com.pps.education.dto.UpdateSubTopicRequest;
import vn.com.pps.education.service.CurriculumService;

/**
 * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-26 — sửa/xóa 1 Sub Topic theo id (tạo mới/
 * liệt kê vẫn ở {@link UnitController} qua {@code /api/units/{unitId}/sub-topics}, mirror cách
 * {@link BookController} sở hữu {@code /api/books/{id}} cho sửa/xóa Sách).
 */
@RestController
@RequestMapping("/api/sub-topics")
public class SubTopicController {

    private final CurriculumService curriculumService;

    public SubTopicController(CurriculumService curriculumService) {
        this.curriculumService = curriculumService;
    }

    @PreAuthorize("hasPermission(null, 'lms.exercise.update')")
    @PutMapping("/{id}")
    public ResponseEntity<SubTopicResponse> updateSubTopic(@PathVariable Long id, @Valid @RequestBody UpdateSubTopicRequest request) {
        return ResponseEntity.ok(curriculumService.updateSubTopic(id, request));
    }

    /** Chỉ xóa được khi chưa Đề/Bộ video nào tham chiếu — xem Javadoc CurriculumService#deleteSubTopic. */
    @PreAuthorize("hasPermission(null, 'lms.exercise.update')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteSubTopic(@PathVariable Long id) {
        curriculumService.deleteSubTopic(id);
        return ResponseEntity.ok().build();
    }
}
