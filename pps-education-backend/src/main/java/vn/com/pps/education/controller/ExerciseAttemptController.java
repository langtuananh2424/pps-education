package vn.com.pps.education.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.com.pps.education.domain.AttemptIntegrityEvent.AttemptType;
import vn.com.pps.education.dto.ExerciseAttemptResponse;
import vn.com.pps.education.dto.IntegrityEventBatchResponse;
import vn.com.pps.education.dto.IntegritySummaryResponse;
import vn.com.pps.education.dto.ListeningHintResponse;
import vn.com.pps.education.dto.ListeningPlayProgressResponse;
import vn.com.pps.education.dto.RecordIntegrityEventsRequest;
import vn.com.pps.education.dto.RecordListeningPlayRequest;
import vn.com.pps.education.dto.SaveAnswerRequest;
import vn.com.pps.education.dto.StudentAnswerResponse;
import vn.com.pps.education.security.AuthenticatedUser;
import vn.com.pps.education.service.ExerciseAttemptService;
import vn.com.pps.education.service.ListeningHintService;
import vn.com.pps.education.service.integrity.AttemptIntegrityService;

import java.util.List;

/** UC-24: Làm bài kiểm tra trực tuyến (FR-LMS-02) + UC-27: Làm bài tập/đề ôn tập (FR-LMS-06) — xem Javadoc ExerciseAttemptService. */
@RestController
public class ExerciseAttemptController {

    private final ExerciseAttemptService exerciseAttemptService;
    private final AttemptIntegrityService attemptIntegrityService;
    private final ListeningHintService listeningHintService;

    public ExerciseAttemptController(ExerciseAttemptService exerciseAttemptService, AttemptIntegrityService attemptIntegrityService,
                                      ListeningHintService listeningHintService) {
        this.exerciseAttemptService = exerciseAttemptService;
        this.attemptIntegrityService = attemptIntegrityService;
        this.listeningHintService = listeningHintService;
    }

    // V128 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-19) — assignmentId bắt buộc: từ
    // khi 1 Bài có thể được giao ĐỘC LẬP nhiều lần (mỗi buổi Nhận xét 1 lần, không còn huỷ lẫn nhau —
    // xem ExerciseService#deliverToClass), không thể suy ra "bản giao nào" chỉ từ exerciseId nữa. FE
    // (AssignmentsTab.tsx) đã có sẵn assignmentId trên từng thẻ BTVN, chỉ cần truyền kèm.
    @PostMapping("/api/exercises/{exerciseId}/attempts")
    public ResponseEntity<ExerciseAttemptResponse> startAttempt(@PathVariable Long exerciseId,
                                                                  @RequestParam Long assignmentId,
                                                                  @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(exerciseAttemptService.startAttempt(exerciseId, assignmentId, actor.userId()));
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

    /** V152 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-25) — xem Javadoc ExerciseAttemptService#revealAnswersAndClose. */
    @PostMapping("/api/attempts/{id}/reveal-and-close")
    public ResponseEntity<ExerciseAttemptResponse> revealAnswersAndClose(@PathVariable Long id,
                                                                            @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(exerciseAttemptService.revealAnswersAndClose(id, actor.userId()));
    }

    /** Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-07-31 — xem Javadoc AttemptIntegrityService. */
    @PostMapping("/api/attempts/{id}/integrity-events")
    public ResponseEntity<IntegrityEventBatchResponse> recordIntegrityEvents(@PathVariable Long id,
                                                                               @Valid @RequestBody RecordIntegrityEventsRequest request,
                                                                               @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(attemptIntegrityService.recordEvents(AttemptType.EXERCISE, id, request, actor.userId()));
    }

    /**
     * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-04 — xem Javadoc
     * AttemptIntegrityService#notifyTeachersForBatchViolation. {@code id} = attempt của Bài ĐẦU TIÊN
     * trong Lô (chỉ để tra học sinh/lớp), KHÔNG phải attempt bị dừng — luồng Lô tự nộp hết mọi Bài đang
     * dở ở FE qua submitAttempt bình thường, endpoint này CHỈ để báo Giáo viên.
     */
    @PostMapping("/api/attempts/{id}/batch-integrity-violation-notify")
    public ResponseEntity<Void> notifyBatchIntegrityViolation(@PathVariable Long id,
                                                                @RequestParam int violationCount,
                                                                @AuthenticationPrincipal AuthenticatedUser actor) {
        attemptIntegrityService.notifyTeachersForBatchViolation(id, violationCount, actor.userId());
        return ResponseEntity.noContent().build();
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

    /** Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-06 — xem Javadoc ListeningHintService#recordPlay. */
    @PostMapping("/api/attempts/{id}/listening-plays")
    public ResponseEntity<ListeningPlayProgressResponse> recordListeningPlay(@PathVariable Long id,
                                                                                @Valid @RequestBody RecordListeningPlayRequest request,
                                                                                @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(listeningHintService.recordPlay(id, request.questionId(), actor.userId()));
    }

    /** Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-06 — xem Javadoc ListeningHintService#getHint. */
    @GetMapping("/api/attempts/{id}/listening-hint")
    public ResponseEntity<ListeningHintResponse> getListeningHint(@PathVariable Long id,
                                                                     @RequestParam Long questionId,
                                                                     @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(listeningHintService.getHint(id, questionId, actor.userId()));
    }
}
