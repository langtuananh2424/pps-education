package vn.com.pps.education.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import vn.com.pps.education.common.ExcelHttpResponses;
import vn.com.pps.education.dto.ExerciseAssignmentQuestionStatsResponse;
import vn.com.pps.education.dto.ExerciseAssignmentStatsResponse;
import vn.com.pps.education.dto.ExerciseAssignmentStudentStatsResponse;
import vn.com.pps.education.security.AuthenticatedUser;
import vn.com.pps.education.service.ExerciseReportService;

import java.util.List;

/** UC-66: Thống kê BTVN theo lớp (FR-ACA-07) — xem Javadoc ExerciseReportService. */
@RestController
public class ExerciseReportController {

    private final ExerciseReportService exerciseReportService;

    public ExerciseReportController(ExerciseReportService exerciseReportService) {
        this.exerciseReportService = exerciseReportService;
    }

    @PreAuthorize("hasPermission(null, 'lms.exercise.report.view')")
    @GetMapping("/api/classes/{classId}/exercise-assignments/stats")
    public ResponseEntity<List<ExerciseAssignmentStatsResponse>> listAssignmentStats(
            @PathVariable Long classId, @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(exerciseReportService.listAssignmentStats(classId, actor.userId()));
    }

    @PreAuthorize("hasPermission(null, 'lms.exercise.report.view')")
    @GetMapping("/api/exercise-assignments/{assignmentId}/stats/students")
    public ResponseEntity<ExerciseAssignmentStudentStatsResponse> getStudentStats(
            @PathVariable Long assignmentId, @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(exerciseReportService.getStudentStats(assignmentId, actor.userId()));
    }

    @PreAuthorize("hasPermission(null, 'lms.exercise.report.view')")
    @GetMapping("/api/homework-skill-batches/{batchId}/stats/students")
    public ResponseEntity<ExerciseAssignmentStudentStatsResponse> getBatchStudentStats(
            @PathVariable Long batchId, @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(exerciseReportService.getBatchStudentStats(batchId, actor.userId()));
    }

    @PreAuthorize("hasPermission(null, 'lms.exercise.report.view')")
    @GetMapping("/api/homework-skill-batches/{batchId}/stats/export")
    public ResponseEntity<byte[]> exportBatchStudentStats(
            @PathVariable Long batchId, @AuthenticationPrincipal AuthenticatedUser actor) {
        byte[] content = exerciseReportService.exportBatchStudentStatsExcel(batchId, actor.userId());
        return ExcelHttpResponses.attachment(content, "thong-ke-btvn-lo-" + batchId + ".xlsx");
    }

    @PreAuthorize("hasPermission(null, 'lms.exercise.report.view')")
    @GetMapping("/api/exercise-assignments/{assignmentId}/stats/questions")
    public ResponseEntity<ExerciseAssignmentQuestionStatsResponse> getQuestionStats(
            @PathVariable Long assignmentId, @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(exerciseReportService.getQuestionStats(assignmentId, actor.userId()));
    }

    @PreAuthorize("hasPermission(null, 'lms.exercise.report.view')")
    @GetMapping("/api/exercise-assignments/{assignmentId}/stats/export")
    public ResponseEntity<byte[]> exportStudentStats(
            @PathVariable Long assignmentId, @AuthenticationPrincipal AuthenticatedUser actor) {
        byte[] content = exerciseReportService.exportStudentStatsExcel(assignmentId, actor.userId());
        return ExcelHttpResponses.attachment(content, "thong-ke-btvn-" + assignmentId + ".xlsx");
    }
}
