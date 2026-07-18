package vn.com.pps.education.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import vn.com.pps.education.dto.ExerciseAttemptResponse;
import vn.com.pps.education.dto.SaveAnswerRequest;
import vn.com.pps.education.dto.StudentAnswerResponse;
import vn.com.pps.education.security.AuthenticatedUser;
import vn.com.pps.education.service.ExerciseAttemptService;

import java.util.List;

/** UC-24: Làm bài kiểm tra trực tuyến (FR-LMS-02) + UC-27: Làm bài tập/đề ôn tập (FR-LMS-06) — xem Javadoc ExerciseAttemptService. */
@RestController
public class ExerciseAttemptController {

    private final ExerciseAttemptService exerciseAttemptService;

    public ExerciseAttemptController(ExerciseAttemptService exerciseAttemptService) {
        this.exerciseAttemptService = exerciseAttemptService;
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
}
