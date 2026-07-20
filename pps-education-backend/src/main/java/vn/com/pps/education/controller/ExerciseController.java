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
import vn.com.pps.education.dto.AddExerciseQuestionRequest;
import vn.com.pps.education.dto.AssignExerciseRequest;
import vn.com.pps.education.dto.CreateExerciseRequest;
import vn.com.pps.education.dto.ExerciseAssignmentResponse;
import vn.com.pps.education.dto.ExerciseQuestionResponse;
import vn.com.pps.education.dto.ExerciseResponse;
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

    @PreAuthorize("hasPermission(null, 'lms.exercise.manage')")
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

    @PreAuthorize("hasPermission(null, 'lms.exercise.manage')")
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

    @GetMapping("/api/classes/{classId}/exercises")
    public ResponseEntity<List<ExerciseAssignmentResponse>> listAssignmentsForClass(@PathVariable Long classId,
                                                                                      @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(exerciseService.listAssignmentsForClass(classId, actor.userId()));
    }

    @PreAuthorize("hasPermission(null, 'lms.exercise.manage')")
    @PostMapping("/api/exercises/{id}/publish")
    public ResponseEntity<ExerciseResponse> publishExercise(@PathVariable Long id,
                                                              @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(exerciseService.publishExercise(id, actor.userId()));
    }

    @PostMapping("/api/exercises/{id}/assign")
    public ResponseEntity<ExerciseAssignmentResponse> assignExercise(@PathVariable Long id,
                                                                       @Valid @RequestBody AssignExerciseRequest request,
                                                                       @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(exerciseService.assignExercise(id, request, actor.userId()));
    }
}
