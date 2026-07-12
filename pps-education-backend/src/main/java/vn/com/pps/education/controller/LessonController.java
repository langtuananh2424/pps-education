package vn.com.pps.education.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import vn.com.pps.education.dto.AddLessonMaterialRequest;
import vn.com.pps.education.dto.CreateLessonRequest;
import vn.com.pps.education.dto.LessonMaterialResponse;
import vn.com.pps.education.dto.LessonResponse;
import vn.com.pps.education.dto.UpdateLessonRequest;
import vn.com.pps.education.security.AuthenticatedUser;
import vn.com.pps.education.service.LessonService;

import java.util.List;

/** UC-23: Quản lý bài giảng (FR-LMS-01) — xem Javadoc LessonService. */
@RestController
public class LessonController {

    private final LessonService lessonService;

    public LessonController(LessonService lessonService) {
        this.lessonService = lessonService;
    }

    @PostMapping("/api/lessons")
    public ResponseEntity<LessonResponse> createLesson(@Valid @RequestBody CreateLessonRequest request,
                                                         @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(lessonService.createLesson(request, actor.userId()));
    }

    @PutMapping("/api/lessons/{id}")
    public ResponseEntity<LessonResponse> updateLesson(@PathVariable Long id,
                                                         @Valid @RequestBody UpdateLessonRequest request,
                                                         @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(lessonService.updateLesson(id, request, actor.userId()));
    }

    @GetMapping("/api/classes/{classId}/lessons")
    public ResponseEntity<List<LessonResponse>> listByClass(@PathVariable Long classId) {
        return ResponseEntity.ok(lessonService.listByClass(classId));
    }

    @GetMapping("/api/curriculums/{curriculumId}/lessons")
    public ResponseEntity<List<LessonResponse>> listByCurriculum(@PathVariable Long curriculumId) {
        return ResponseEntity.ok(lessonService.listByCurriculum(curriculumId));
    }

    @PostMapping("/api/lessons/{lessonId}/materials")
    public ResponseEntity<LessonMaterialResponse> addMaterial(@PathVariable Long lessonId,
                                                                @Valid @RequestBody AddLessonMaterialRequest request,
                                                                @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(lessonService.addMaterial(lessonId, request, actor.userId()));
    }

    @GetMapping("/api/lessons/{lessonId}/materials")
    public ResponseEntity<List<LessonMaterialResponse>> listMaterials(@PathVariable Long lessonId) {
        return ResponseEntity.ok(lessonService.listMaterials(lessonId));
    }
}
