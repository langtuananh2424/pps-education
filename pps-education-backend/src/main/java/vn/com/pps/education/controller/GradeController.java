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
import vn.com.pps.education.dto.CreateGradeComponentRequest;
import vn.com.pps.education.dto.CreateGradePeriodRequest;
import vn.com.pps.education.dto.EnterGradePeriodResultRequest;
import vn.com.pps.education.dto.EnterGradeRequest;
import vn.com.pps.education.dto.GradeComponentResponse;
import vn.com.pps.education.dto.GradeEntryResponse;
import vn.com.pps.education.dto.GradePeriodResponse;
import vn.com.pps.education.dto.GradePeriodResultResponse;
import vn.com.pps.education.dto.PublishGradesRequest;
import vn.com.pps.education.dto.SubmitGradesRequest;
import vn.com.pps.education.dto.UpdateGradeComponentRequest;
import vn.com.pps.education.dto.UpdateGradePeriodRequest;
import vn.com.pps.education.security.AuthenticatedUser;
import vn.com.pps.education.service.GradeService;

import java.util.List;

/** UC-19: Nhập điểm (FR-ACA-03) + UC-20: Duyệt/Từ chối điểm (FR-ACA-03) — xem Javadoc GradeService. */
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

    @PreAuthorize("hasPermission(null, 'academic.grade.period.create')")
    @PostMapping("/api/curriculums/{curriculumId}/grade-periods")
    public ResponseEntity<GradePeriodResponse> createGradePeriod(@PathVariable Long curriculumId,
                                                                     @Valid @RequestBody CreateGradePeriodRequest request,
                                                                     @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(gradeService.createGradePeriod(curriculumId, request, actor.userId()));
    }

    @PreAuthorize("hasPermission(null, 'academic.grade.period.update')")
    @PutMapping("/api/grade-periods/{id}")
    public ResponseEntity<GradePeriodResponse> updateGradePeriod(@PathVariable Long id,
                                                                     @Valid @RequestBody UpdateGradePeriodRequest request,
                                                                     @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(gradeService.updateGradePeriod(id, request, actor.userId()));
    }

    /** UC-19 (bổ sung): xoá kỳ đánh giá rỗng — xem Javadoc GradeService.deleteGradePeriod. */
    @PreAuthorize("hasPermission(null, 'academic.grade.period.delete')")
    @DeleteMapping("/api/grade-periods/{id}")
    public ResponseEntity<Void> deleteGradePeriod(@PathVariable Long id,
                                                  @AuthenticationPrincipal AuthenticatedUser actor) {
        gradeService.deleteGradePeriod(id, actor.userId());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/grade-periods/{gradePeriodId}/components")
    public ResponseEntity<List<GradeComponentResponse>> listGradeComponents(@PathVariable Long gradePeriodId) {
        return ResponseEntity.ok(gradeService.listGradeComponents(gradePeriodId));
    }

    @PreAuthorize("hasPermission(null, 'academic.grade.component.create')")
    @PostMapping("/api/grade-periods/{gradePeriodId}/components")
    public ResponseEntity<GradeComponentResponse> addGradeComponent(@PathVariable Long gradePeriodId,
                                                                        @Valid @RequestBody CreateGradeComponentRequest request,
                                                                        @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(gradeService.addGradeComponent(gradePeriodId, request, actor.userId()));
    }

    @PreAuthorize("hasPermission(null, 'academic.grade.component.update')")
    @PutMapping("/api/grade-components/{id}")
    public ResponseEntity<GradeComponentResponse> updateGradeComponent(@PathVariable Long id,
                                                                           @Valid @RequestBody UpdateGradeComponentRequest request,
                                                                           @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(gradeService.updateGradeComponent(id, request, actor.userId()));
    }

    /** UC-19 (bổ sung): xoá thành phần điểm chưa có điểm nhập — xem Javadoc GradeService.deleteGradeComponent. */
    @PreAuthorize("hasPermission(null, 'academic.grade.component.delete')")
    @DeleteMapping("/api/grade-components/{id}")
    public ResponseEntity<Void> deleteGradeComponent(@PathVariable Long id,
                                                     @AuthenticationPrincipal AuthenticatedUser actor) {
        gradeService.deleteGradeComponent(id, actor.userId());
        return ResponseEntity.noContent().build();
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

    /** UC-19 (xoá điểm nháp, bổ sung ngoài SDD gốc, đã xác nhận với người dùng) — chỉ xoá được bản ghi DRAFT (hoặc academic.grade.edit.override). */
    @DeleteMapping("/api/classes/{classId}/grades/components/{gradeComponentId}/students/{studentId}")
    public ResponseEntity<Void> deleteGradeEntry(@PathVariable Long classId, @PathVariable Long gradeComponentId,
                                                  @PathVariable Long studentId,
                                                  @AuthenticationPrincipal AuthenticatedUser actor) {
        gradeService.deleteGradeEntry(classId, gradeComponentId, studentId, actor.userId());
        return ResponseEntity.noContent().build();
    }

    /** UC-19 Main Flow bước 4 (V44): Giáo viên gửi duyệt — DRAFT/REJECTED -> SUBMITTED, chờ Quản lý điểm trường duyệt qua UC-20. */
    @PostMapping("/api/grades/submit")
    public ResponseEntity<List<GradeEntryResponse>> submitGradesForApproval(@Valid @RequestBody SubmitGradesRequest request,
                                                                              @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(gradeService.submitGradesForApproval(request, actor.userId()));
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

    /** UC-53 (xoá điểm tổng kết kỳ nháp, bổ sung ngoài SDD gốc, đã xác nhận với người dùng) — chỉ xoá được bản ghi DRAFT (hoặc academic.grade.edit.override). */
    @DeleteMapping("/api/classes/{classId}/grades/students/{studentId}/periods/{gradePeriodId}/result")
    public ResponseEntity<Void> deletePeriodResult(@PathVariable Long classId, @PathVariable Long studentId,
                                                    @PathVariable Long gradePeriodId,
                                                    @AuthenticationPrincipal AuthenticatedUser actor) {
        gradeService.deletePeriodResult(classId, studentId, gradePeriodId, actor.userId());
        return ResponseEntity.noContent().build();
    }

    // ---- UC-20: Duyệt/Từ chối điểm (SITE_MANAGER + HEAD_ACADEMIC) ----

    @GetMapping("/api/grades/pending")
    public ResponseEntity<List<GradeEntryResponse>> listUnpublishedForSite(@AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(gradeService.listUnpublishedForSite(actor.userId()));
    }

    @PostMapping("/api/grades/decision")
    public ResponseEntity<List<GradeEntryResponse>> publishGrades(@Valid @RequestBody PublishGradesRequest request,
                                                                     @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(gradeService.publishGrades(request, actor.userId()));
    }
}
