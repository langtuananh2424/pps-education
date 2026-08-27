package vn.com.pps.education.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.com.pps.education.dto.CreateSubTopicRequest;
import vn.com.pps.education.dto.SubTopicResponse;
import vn.com.pps.education.dto.UnitResponse;
import vn.com.pps.education.dto.UpdateUnitRequest;
import vn.com.pps.education.service.CurriculumService;

import java.util.List;

/** V144 (Kho đề — Sách/Khối -&gt; Unit -&gt; Sub Topic -&gt; Lesson -&gt; Bài): Sub Topic con của 1 Unit. */
@RestController
@RequestMapping("/api/units")
public class UnitController {

    private final CurriculumService curriculumService;

    public UnitController(CurriculumService curriculumService) {
        this.curriculumService = curriculumService;
    }

    @GetMapping("/{unitId}/sub-topics")
    public ResponseEntity<List<SubTopicResponse>> listSubTopics(@PathVariable Long unitId) {
        return ResponseEntity.ok(curriculumService.listSubTopics(unitId));
    }

    // V144 — mirror CurriculumController#addUnit, dùng chung lms.exercise.create (xem Javadoc ở đó).
    @PreAuthorize("hasPermission(null, 'lms.exercise.create')")
    @PostMapping("/{unitId}/sub-topics")
    public ResponseEntity<SubTopicResponse> addSubTopic(@PathVariable Long unitId,
                                                            @Valid @RequestBody CreateSubTopicRequest request) {
        return ResponseEntity.ok(curriculumService.addSubTopic(unitId, request));
    }

    // Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-26 — sửa/xóa 1 Unit, dùng chung quyền
    // lms.exercise.update.
    @PreAuthorize("hasPermission(null, 'lms.exercise.update')")
    @PutMapping("/{id}")
    public ResponseEntity<UnitResponse> updateUnit(@PathVariable Long id, @Valid @RequestBody UpdateUnitRequest request) {
        return ResponseEntity.ok(curriculumService.updateUnit(id, request));
    }

    /** Chỉ xóa được khi Unit đã hết Sub Topic — xem Javadoc CurriculumService#deleteUnit. */
    @PreAuthorize("hasPermission(null, 'lms.exercise.update')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUnit(@PathVariable Long id) {
        curriculumService.deleteUnit(id);
        return ResponseEntity.ok().build();
    }
}
