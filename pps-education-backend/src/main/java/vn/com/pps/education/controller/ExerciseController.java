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
import org.springframework.web.bind.annotation.RestController;
import vn.com.pps.education.dto.AddExerciseQuestionRequest;
import vn.com.pps.education.dto.CreateExerciseRequest;
import vn.com.pps.education.dto.ExerciseAssignmentResponse;
import vn.com.pps.education.dto.ExerciseQuestionResponse;
import vn.com.pps.education.dto.ExerciseResponse;
import vn.com.pps.education.dto.UpdateExerciseQuestionPointsRequest;
import vn.com.pps.education.dto.UpdateExerciseRequest;
import vn.com.pps.education.security.AuthenticatedUser;
import vn.com.pps.education.service.ExerciseService;

import java.util.List;

/** UC-40: Soạn & giao đề kiểm tra (FR-LMS-10) — phần lắp đề/giao đề, xem Javadoc ExerciseService. */
@RestController
public class ExerciseController {

    private final ExerciseService exerciseService;

    public ExerciseController(ExerciseService exerciseService) {
        this.exerciseService = exerciseService;
    }

    @PreAuthorize("hasPermission(null, 'lms.exercise.create')")
    @PostMapping("/api/exercises")
    public ResponseEntity<ExerciseResponse> createExercise(@Valid @RequestBody CreateExerciseRequest request,
                                                             @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(exerciseService.createExercise(request, actor.userId()));
    }

    @GetMapping("/api/exercises/{id}")
    public ResponseEntity<ExerciseResponse> getExercise(@PathVariable Long id,
                                                          @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(exerciseService.getExercise(id, actor.userId()));
    }

    @PreAuthorize("hasPermission(null, 'lms.exercise.update')")
    @PutMapping("/api/exercises/{id}")
    public ResponseEntity<ExerciseResponse> updateExercise(@PathVariable Long id, @Valid @RequestBody UpdateExerciseRequest request,
                                                             @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(exerciseService.updateExercise(id, request, actor.userId()));
    }

    /** V87 — "Xóa Bài" (lưu trữ, status=ARCHIVED) — xem Javadoc ExerciseService#deleteExercise. */
    @PreAuthorize("hasPermission(null, 'lms.exercise.update')")
    @DeleteMapping("/api/exercises/{id}")
    public ResponseEntity<Void> deleteExercise(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser actor) {
        exerciseService.deleteExercise(id, actor.userId());
        return ResponseEntity.noContent().build();
    }

    @PreAuthorize("hasPermission(null, 'lms.exercise.update')")
    @PostMapping("/api/exercises/{id}/questions")
    public ResponseEntity<ExerciseQuestionResponse> addQuestion(@PathVariable Long id,
                                                                  @Valid @RequestBody AddExerciseQuestionRequest request,
                                                                  @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(exerciseService.addQuestion(id, request, actor.userId()));
    }

    @GetMapping("/api/exercises/{id}/questions")
    public ResponseEntity<List<ExerciseQuestionResponse>> listQuestions(@PathVariable Long id,
                                                                          @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(exerciseService.listQuestions(id, actor.userId()));
    }

    /** Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-18 — sửa điểm 1 câu hỏi đã gắn vào Bài, chỉ khi còn DRAFT. */
    @PreAuthorize("hasPermission(null, 'lms.exercise.update')")
    @PutMapping("/api/exercises/{id}/questions/{exerciseQuestionId}/points")
    public ResponseEntity<ExerciseQuestionResponse> updateQuestionPoints(@PathVariable Long id,
                                                                          @PathVariable Long exerciseQuestionId,
                                                                          @Valid @RequestBody UpdateExerciseQuestionPointsRequest request,
                                                                          @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(exerciseService.updateQuestionPoints(id, exerciseQuestionId, request, actor.userId()));
    }

    /** Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-07-31 — gỡ câu hỏi khỏi Bài, chỉ khi còn DRAFT. */
    @PreAuthorize("hasPermission(null, 'lms.exercise.update')")
    @DeleteMapping("/api/exercises/{id}/questions/{exerciseQuestionId}")
    public ResponseEntity<Void> removeQuestion(@PathVariable Long id,
                                                @PathVariable Long exerciseQuestionId,
                                                @AuthenticationPrincipal AuthenticatedUser actor) {
        exerciseService.removeQuestion(id, exerciseQuestionId, actor.userId());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/classes/{classId}/exercises")
    public ResponseEntity<List<ExerciseAssignmentResponse>> listAssignmentsForClass(@PathVariable Long classId,
                                                                                      @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(exerciseService.listAssignmentsForClass(classId, actor.userId()));
    }

    /** Kho đề — nguồn cho dropdown "BTVN buổi sau" ở Nhận xét học viên: Bài đã Publish, thuộc 1 Đề đã gán cho lớp. */
    @GetMapping("/api/classes/{classId}/exercises/published")
    public ResponseEntity<List<ExerciseResponse>> listPublishedForClass(@PathVariable Long classId,
                                                                          @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(exerciseService.listPublishedForClass(classId, actor.userId()));
    }

    /** V65 — Publish giờ chỉ đánh dấu đề "đủ điều kiện dùng làm nguồn", không còn giao lớp (xem Javadoc ExerciseService). */
    @PreAuthorize("hasPermission(null, 'lms.exercise.publish')")
    @PostMapping("/api/exercises/{id}/publish")
    public ResponseEntity<ExerciseResponse> publishExercise(@PathVariable Long id,
                                                              @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(exerciseService.publishExercise(id, actor.userId()));
    }
}
