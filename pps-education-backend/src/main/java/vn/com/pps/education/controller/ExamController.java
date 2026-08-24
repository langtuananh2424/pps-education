package vn.com.pps.education.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.com.pps.education.dto.ClassResponse;
import vn.com.pps.education.dto.CreateExamRequest;
import vn.com.pps.education.dto.ExamResponse;
import vn.com.pps.education.dto.ExerciseResponse;
import vn.com.pps.education.dto.UpdateExamRequest;
import vn.com.pps.education.security.AuthenticatedUser;
import vn.com.pps.education.service.ExamService;

import java.util.List;

/** Kho đề (UC-40, bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-07-30) — xem Javadoc ExamService. */
@RestController
public class ExamController {

    private final ExamService examService;

    public ExamController(ExamService examService) {
        this.examService = examService;
    }

    @PreAuthorize("hasPermission(null, 'lms.exam.create')")
    @PostMapping("/api/exams")
    public ResponseEntity<ExamResponse> createExam(@Valid @RequestBody CreateExamRequest request,
                                                     @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(examService.createExam(request, actor.userId()));
    }

    @GetMapping("/api/exams")
    public ResponseEntity<List<ExamResponse>> listExams(@RequestParam(required = false) Long curriculumId,
                                                          @RequestParam(required = false) String teacherType,
                                                          @RequestParam(required = false) String skillCategory,
                                                          @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(examService.listExams(curriculumId, teacherType, skillCategory, actor.userId()));
    }

    @GetMapping("/api/exams/{id}")
    public ResponseEntity<ExamResponse> getExam(@PathVariable Long id,
                                                 @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(examService.getExam(id, actor.userId()));
    }

    @PreAuthorize("hasPermission(null, 'lms.exam.update')")
    @PutMapping("/api/exams/{id}")
    public ResponseEntity<ExamResponse> updateExam(@PathVariable Long id, @Valid @RequestBody UpdateExamRequest request,
                                                     @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(examService.updateExam(id, request, actor.userId()));
    }

    @GetMapping("/api/exams/{id}/exercises")
    public ResponseEntity<List<ExerciseResponse>> listExercises(@PathVariable Long id,
                                                                  @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(examService.listExercises(id, actor.userId()));
    }

    /** V144 — nguồn cho dropdown "giao cả Đề" ở Nhận xét học viên (UC-21, mirror ExerciseController#listPublishedForClass). */
    @GetMapping("/api/classes/{classId}/exams/published")
    public ResponseEntity<List<ExamResponse>> listPublishedForClass(@PathVariable Long classId,
                                                                      @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(examService.listPublishedForClass(classId, actor.userId()));
    }

    @PreAuthorize("hasPermission(null, 'lms.exam.assign')")
    @PostMapping("/api/exams/{id}/classes/{classId}")
    public ResponseEntity<Void> assignToClass(@PathVariable Long id, @PathVariable Long classId,
                                                @AuthenticationPrincipal AuthenticatedUser actor) {
        examService.assignToClass(id, classId, actor.userId());
        return ResponseEntity.ok().build();
    }

    @PreAuthorize("hasPermission(null, 'lms.exam.assign')")
    @DeleteMapping("/api/exams/{id}/classes/{classId}")
    public ResponseEntity<Void> unassignFromClass(@PathVariable Long id, @PathVariable Long classId,
                                                    @AuthenticationPrincipal AuthenticatedUser actor) {
        examService.unassignFromClass(id, classId, actor.userId());
        return ResponseEntity.ok().build();
    }

    @GetMapping("/api/exams/{id}/classes")
    public ResponseEntity<List<ClassResponse>> listAssignedClasses(@PathVariable Long id,
                                                                     @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(examService.listAssignedClasses(id, actor.userId()));
    }

    /** V87 — "Xóa Đề" (soft-delete), chỉ xóa được khi mọi Bài thuộc Đề đã lưu trữ — xem Javadoc ExamService#deleteExam. */
    @PreAuthorize("hasPermission(null, 'lms.exam.delete')")
    @DeleteMapping("/api/exams/{id}")
    public ResponseEntity<Void> deleteExam(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser actor) {
        examService.deleteExam(id, actor.userId());
        return ResponseEntity.noContent().build();
    }
}
