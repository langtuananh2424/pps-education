package vn.com.pps.education.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import vn.com.pps.education.dto.CreateGradeComponentRequest;
import vn.com.pps.education.dto.CreateGradePeriodRequest;
import vn.com.pps.education.dto.DecideGradesRequest;
import vn.com.pps.education.dto.EnterGradePeriodResultRequest;
import vn.com.pps.education.dto.EnterGradeRequest;
import vn.com.pps.education.dto.GradeComponentResponse;
import vn.com.pps.education.dto.GradeEntryResponse;
import vn.com.pps.education.dto.GradePeriodResponse;
import vn.com.pps.education.dto.GradePeriodResultResponse;
import vn.com.pps.education.dto.PeriodAverageResponse;
import vn.com.pps.education.dto.SubmitGradesRequest;
import vn.com.pps.education.dto.UpdateGradeComponentRequest;
import vn.com.pps.education.dto.UpdateGradePeriodRequest;
import vn.com.pps.education.security.AuthenticatedUser;
import vn.com.pps.education.service.GradeService;

import java.util.List;

/** UC-19: Nhập điểm (FR-ACA-03) + UC-20: Duyệt điểm (FR-ACA-03) — xem Javadoc GradeService. */
@RestController
public class GradeController {

    private final GradeService gradeService;

    public GradeController(GradeService gradeService) {
        this.gradeService = gradeService;
    }

    // ---- Cấu hình sổ điểm (HEAD_ACADEMIC) ----

    @GetMapping("/api/curriculums/{curriculumId}/grade-periods")
    public ResponseEntity<List<GradePeriodResponse>> listGradePeriods(@PathVariable Long curriculumId) {
        return ResponseEntity.ok(gradeService.listGradePeriods(curriculumId));
    }

    @PreAuthorize("hasPermission(null, 'academic.grade.manage')")
    @PostMapping("/api/curriculums/{curriculumId}/grade-periods")
    public ResponseEntity<GradePeriodResponse> createGradePeriod(@PathVariable Long curriculumId,
                                                                     @Valid @RequestBody CreateGradePeriodRequest request,
                                                                     @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(gradeService.createGradePeriod(curriculumId, request, actor.userId()));
    }

    @PreAuthorize("hasPermission(null, 'academic.grade.manage')")
    @PutMapping("/api/grade-periods/{id}")
    public ResponseEntity<GradePeriodResponse> updateGradePeriod(@PathVariable Long id,
                                                                     @Valid @RequestBody UpdateGradePeriodRequest request,
                                                                     @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(gradeService.updateGradePeriod(id, request, actor.userId()));
    }

    @GetMapping("/api/grade-periods/{gradePeriodId}/components")
    public ResponseEntity<List<GradeComponentResponse>> listGradeComponents(@PathVariable Long gradePeriodId) {
        return ResponseEntity.ok(gradeService.listGradeComponents(gradePeriodId));
    }

    @PreAuthorize("hasPermission(null, 'academic.grade.manage')")
    @PostMapping("/api/grade-periods/{gradePeriodId}/components")
    public ResponseEntity<GradeComponentResponse> addGradeComponent(@PathVariable Long gradePeriodId,
                                                                        @Valid @RequestBody CreateGradeComponentRequest request,
                                                                        @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(gradeService.addGradeComponent(gradePeriodId, request, actor.userId()));
    }

    @PreAuthorize("hasPermission(null, 'academic.grade.manage')")
    @PutMapping("/api/grade-components/{id}")
    public ResponseEntity<GradeComponentResponse> updateGradeComponent(@PathVariable Long id,
                                                                           @Valid @RequestBody UpdateGradeComponentRequest request,
                                                                           @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(gradeService.updateGradeComponent(id, request, actor.userId()));
    }

    // ---- UC-19: Nhập điểm (TEACHER + HEAD_ACADEMIC/SITE_MANAGER hỗ trợ) ----

    @GetMapping("/api/classes/{classId}/grades/components/{gradeComponentId}")
    public ResponseEntity<List<GradeEntryResponse>> listEntries(@PathVariable Long classId, @PathVariable Long gradeComponentId) {
        return ResponseEntity.ok(gradeService.listEntries(classId, gradeComponentId));
    }

    @PostMapping("/api/classes/{classId}/grades/components/{gradeComponentId}")
    public ResponseEntity<GradeEntryResponse> enterGrade(@PathVariable Long classId, @PathVariable Long gradeComponentId,
                                                             @Valid @RequestBody EnterGradeRequest request,
                                                             @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(gradeService.enterGrade(classId, gradeComponentId, request, actor.userId()));
    }

    @PostMapping("/api/classes/{classId}/grades/submit")
    public ResponseEntity<List<GradeEntryResponse>> submitGrades(@PathVariable Long classId,
                                                                     @Valid @RequestBody SubmitGradesRequest request,
                                                                     @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(gradeService.submitGrades(classId, request, actor.userId()));
    }

    @GetMapping("/api/classes/{classId}/grades/students/{studentId}/periods/{gradePeriodId}/average")
    public ResponseEntity<PeriodAverageResponse> getPeriodAverage(@PathVariable Long classId, @PathVariable Long studentId,
                                                                      @PathVariable Long gradePeriodId) {
        return ResponseEntity.ok(gradeService.getPeriodAverage(classId, studentId, gradePeriodId));
    }

    // ---- UC-53: Overall/Level theo kỳ đánh giá (TEACHER + HEAD_ACADEMIC/SITE_MANAGER hỗ trợ) ----

    @PostMapping("/api/classes/{classId}/grades/students/{studentId}/periods/{gradePeriodId}/result")
    public ResponseEntity<GradePeriodResultResponse> enterPeriodResult(@PathVariable Long classId,
                                                                       @PathVariable Long studentId,
                                                                       @PathVariable Long gradePeriodId,
                                                                       @Valid @RequestBody EnterGradePeriodResultRequest request,
                                                                       @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(gradeService.enterPeriodResult(classId, studentId, gradePeriodId, request, actor.userId()));
    }

    @GetMapping("/api/classes/{classId}/grade-periods/{gradePeriodId}/results")
    public ResponseEntity<List<GradePeriodResultResponse>> listPeriodResults(@PathVariable Long classId,
                                                                             @PathVariable Long gradePeriodId) {
        return ResponseEntity.ok(gradeService.listPeriodResults(classId, gradePeriodId));
    }

    // ---- UC-20: Duyệt điểm (SITE_MANAGER + HEAD_ACADEMIC) ----

    @GetMapping("/api/grades/pending")
    public ResponseEntity<List<GradeEntryResponse>> listPendingForSite(@AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(gradeService.listPendingForSite(actor.userId()));
    }

    @PostMapping("/api/grades/decision")
    public ResponseEntity<List<GradeEntryResponse>> decideGrades(@Valid @RequestBody DecideGradesRequest request,
                                                                     @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(gradeService.decideGrades(request, actor.userId()));
    }
}
