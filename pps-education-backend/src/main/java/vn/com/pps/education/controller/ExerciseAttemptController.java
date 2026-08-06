package vn.com.pps.education.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import vn.com.pps.education.domain.AttemptIntegrityEvent.AttemptType;
import vn.com.pps.education.dto.ExerciseAttemptResponse;
import vn.com.pps.education.dto.IntegrityEventBatchResponse;
import vn.com.pps.education.dto.IntegritySummaryResponse;
import vn.com.pps.education.dto.RecordIntegrityEventsRequest;
import vn.com.pps.education.dto.SaveAnswerRequest;
import vn.com.pps.education.dto.StudentAnswerResponse;
import vn.com.pps.education.security.AuthenticatedUser;
import vn.com.pps.education.service.ExerciseAttemptService;
import vn.com.pps.education.service.integrity.AttemptIntegrityService;

import java.util.List;

/** UC-24: Làm bài kiểm tra trực tuyến (FR-LMS-02) + UC-27: Làm bài tập/đề ôn tập (FR-LMS-06) — xem Javadoc ExerciseAttemptService. */
@RestController
public class ExerciseAttemptController {

    private final ExerciseAttemptService exerciseAttemptService;
    private final AttemptIntegrityService attemptIntegrityService;

    public ExerciseAttemptController(ExerciseAttemptService exerciseAttemptService, AttemptIntegrityService attemptIntegrityService) {
        this.exerciseAttemptService = exerciseAttemptService;
        this.attemptIntegrityService = attemptIntegrityService;
    }

    @PostMapping("/api/exercises/{exerciseId}/attempts")
    public ResponseEntity<ExerciseAttemptResponse> startAttempt(@PathVariable Long exerciseId,
                                                                  @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(exerciseAttemptService.startAttempt(exerciseId, actor.userId()));
    }

    @GetMapping("/api/exercises/{exerciseId}/attempts")
    public ResponseEntity<List<ExerciseAttemptResponse>> listMyAttempts(@PathVariable Long exerciseId,
                                                                          @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(exerciseAttemptService.listMyAttempts(exerciseId, actor.userId()));
    }

    @GetMapping("/api/attempts/{id}")
    public ResponseEntity<ExerciseAttemptResponse> getAttempt(@PathVariable Long id,
                                                                @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(exerciseAttemptService.getAttempt(id, actor.userId()));
    }

    @PostMapping("/api/attempts/{id}/answers")
    public ResponseEntity<StudentAnswerResponse> saveAnswer(@PathVariable Long id,
                                                              @Valid @RequestBody SaveAnswerRequest request,
                                                              @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(exerciseAttemptService.saveAnswer(id, request, actor.userId()));
    }

    @GetMapping("/api/attempts/{id}/answers")
    public ResponseEntity<List<StudentAnswerResponse>> listAnswers(@PathVariable Long id,
                                                                     @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(exerciseAttemptService.listAnswers(id, actor.userId()));
    }

    @PostMapping("/api/attempts/{id}/submit")
    public ResponseEntity<ExerciseAttemptResponse> submitAttempt(@PathVariable Long id,
                                                                   @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(exerciseAttemptService.submitAttempt(id, actor.userId()));
    }

    /** Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-07-31 — xem Javadoc AttemptIntegrityService. */
    @PostMapping("/api/attempts/{id}/integrity-events")
    public ResponseEntity<IntegrityEventBatchResponse> recordIntegrityEvents(@PathVariable Long id,
                                                                               @Valid @RequestBody RecordIntegrityEventsRequest request,
                                                                               @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(attemptIntegrityService.recordEvents(AttemptType.EXERCISE, id, request, actor.userId()));
    }

    /** Giáo viên xem tổng hợp vi phạm của 1 lượt làm bài khi chấm. */
    @PreAuthorize("hasPermission(null, 'lms.grading.manage')")
    @GetMapping("/api/attempts/{id}/integrity-summary")
    public ResponseEntity<IntegritySummaryResponse> getIntegritySummary(@PathVariable Long id) {
        return ResponseEntity.ok(attemptIntegrityService.getSummary(AttemptType.EXERCISE, id));
    }

    /** Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-06 — xem Javadoc ExerciseAttemptService#listAttemptsForGrading. */
    @PreAuthorize("hasPermission(null, 'lms.grading.manage')")
    @GetMapping("/api/exercises/{exerciseId}/students/{studentId}/attempts")
    public ResponseEntity<List<ExerciseAttemptResponse>> listAttemptsForGrading(@PathVariable Long exerciseId,
                                                                                  @PathVariable Long studentId) {
        return ResponseEntity.ok(exerciseAttemptService.listAttemptsForGrading(exerciseId, studentId));
    }

    /** Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-06 — xem Javadoc ExerciseAttemptService#listAnswersForGrading. */
    @PreAuthorize("hasPermission(null, 'lms.grading.manage')")
    @GetMapping("/api/attempts/{id}/answers/for-grading")
    public ResponseEntity<List<StudentAnswerResponse>> listAnswersForGrading(@PathVariable Long id) {
        return ResponseEntity.ok(exerciseAttemptService.listAnswersForGrading(id));
    }

    /** Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-06 — xem Javadoc ExerciseAttemptService#selectForGrading. */
    @PreAuthorize("hasPermission(null, 'lms.grading.manage')")
    @PostMapping("/api/attempts/{id}/select-for-grading")
    public ResponseEntity<List<ExerciseAttemptResponse>> selectForGrading(@PathVariable Long id,
                                                                             @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(exerciseAttemptService.selectForGrading(id, actor.userId()));
    }
}
