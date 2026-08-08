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
import vn.com.pps.education.dto.CreateGradeComponentSetupRequest;
import vn.com.pps.education.dto.CreateGradeEvaluationComponentRequest;
import vn.com.pps.education.dto.EnterGradeEvaluationResultRequest;
import vn.com.pps.education.dto.EnterGradeRequest;
import vn.com.pps.education.dto.GradeComponentSetupResponse;
import vn.com.pps.education.dto.GradeEntryResponse;
import vn.com.pps.education.dto.GradeEvaluationComponentResponse;
import vn.com.pps.education.dto.GradeEvaluationResultResponse;
import vn.com.pps.education.dto.PublishGradesRequest;
import vn.com.pps.education.dto.StudentResponse;
import vn.com.pps.education.dto.SubmitGradesRequest;
import vn.com.pps.education.dto.UpdateGradeComponentSetupRequest;
import vn.com.pps.education.dto.UpdateGradeEvaluationComponentRequest;
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

    // ---- Cấu hình sổ điểm (HEAD_ACADEMIC/SUPER_ADMIN) — V95, gắn theo (lớp, kỳ học, Giữa/Cuối kỳ) ----

    @GetMapping("/api/classes/{classId}/grade-component-setups")
    public ResponseEntity<List<GradeComponentSetupResponse>> listGradeComponentSetups(
            @PathVariable Long classId, @RequestParam(required = false) Long academicTermId) {
        return ResponseEntity.ok(gradeService.listGradeComponentSetups(classId, academicTermId));
    }

    @PreAuthorize("hasPermission(null, 'academic.grade.setup.create')")
    @PostMapping("/api/classes/{classId}/grade-component-setups")
    public ResponseEntity<GradeComponentSetupResponse> createGradeComponentSetup(
            @PathVariable Long classId,
            @Valid @RequestBody CreateGradeComponentSetupRequest request,
            @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(gradeService.createGradeComponentSetup(classId, request, actor.userId()));
    }

    @PreAuthorize("hasPermission(null, 'academic.grade.setup.update')")
    @PutMapping("/api/grade-component-setups/{id}")
    public ResponseEntity<GradeComponentSetupResponse> updateGradeComponentSetup(
            @PathVariable Long id,
            @Valid @RequestBody UpdateGradeComponentSetupRequest request,
            @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(gradeService.updateGradeComponentSetup(id, request, actor.userId()));
    }

    /** UC-19 (bổ sung): xoá setup sổ điểm rỗng — xem Javadoc GradeService.deleteGradeComponentSetup. */
    @PreAuthorize("hasPermission(null, 'academic.grade.setup.delete')")
    @DeleteMapping("/api/grade-component-setups/{id}")
    public ResponseEntity<Void> deleteGradeComponentSetup(@PathVariable Long id,
                                                           @AuthenticationPrincipal AuthenticatedUser actor) {
        gradeService.deleteGradeComponentSetup(id, actor.userId());
        return ResponseEntity.noContent().build();
    }

    /** V95 (mới): danh sách học sinh của 1 setup — TÍNH RA theo rosterAsOfDate, xem Javadoc GradeService.getRoster. */
    @GetMapping("/api/grade-component-setups/{id}/roster")
    public ResponseEntity<List<StudentResponse>> getRoster(@PathVariable Long id) {
        return ResponseEntity.ok(gradeService.getRoster(id));
    }

    @GetMapping("/api/grade-component-setups/{setupId}/components")
    public ResponseEntity<List<GradeEvaluationComponentResponse>> listGradeEvaluationComponents(@PathVariable Long setupId) {
        return ResponseEntity.ok(gradeService.listGradeEvaluationComponents(setupId));
    }

    @PreAuthorize("hasPermission(null, 'academic.grade.component.create')")
    @PostMapping("/api/grade-component-setups/{setupId}/components")
    public ResponseEntity<GradeEvaluationComponentResponse> addGradeEvaluationComponent(
            @PathVariable Long setupId,
            @Valid @RequestBody CreateGradeEvaluationComponentRequest request,
            @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(gradeService.addGradeEvaluationComponent(setupId, request, actor.userId()));
    }

    @PreAuthorize("hasPermission(null, 'academic.grade.component.update')")
    @PutMapping("/api/grade-evaluation-components/{id}")
    public ResponseEntity<GradeEvaluationComponentResponse> updateGradeEvaluationComponent(
            @PathVariable Long id,
            @Valid @RequestBody UpdateGradeEvaluationComponentRequest request,
            @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(gradeService.updateGradeEvaluationComponent(id, request, actor.userId()));
    }

    /** UC-19 (bổ sung): xoá thành phần điểm chưa có điểm nhập — xem Javadoc GradeService.deleteGradeEvaluationComponent. */
    @PreAuthorize("hasPermission(null, 'academic.grade.component.delete')")
    @DeleteMapping("/api/grade-evaluation-components/{id}")
    public ResponseEntity<Void> deleteGradeEvaluationComponent(@PathVariable Long id,
                                                                @AuthenticationPrincipal AuthenticatedUser actor) {
        gradeService.deleteGradeEvaluationComponent(id, actor.userId());
        return ResponseEntity.noContent().build();
    }

    // ---- UC-19: Nhập điểm (TEACHER + HEAD_ACADEMIC/SITE_MANAGER hỗ trợ) ----

    @GetMapping("/api/classes/{classId}/grades/components/{gradeEvaluationComponentId}")
    public ResponseEntity<List<GradeEntryResponse>> listEntries(@PathVariable Long classId, @PathVariable Long gradeEvaluationComponentId) {
        return ResponseEntity.ok(gradeService.listEntries(classId, gradeEvaluationComponentId));
    }

    @PostMapping("/api/classes/{classId}/grades/components/{gradeEvaluationComponentId}")
    public ResponseEntity<GradeEntryResponse> enterGrade(@PathVariable Long classId, @PathVariable Long gradeEvaluationComponentId,
                                                             @Valid @RequestBody EnterGradeRequest request,
                                                             @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(gradeService.enterGrade(classId, gradeEvaluationComponentId, request, actor.userId()));
    }

    /** UC-19 (xoá điểm nháp, bổ sung ngoài SDD gốc, đã xác nhận với người dùng) — chỉ xoá được bản ghi DRAFT (hoặc academic.grade.edit.override). */
    @DeleteMapping("/api/classes/{classId}/grades/components/{gradeEvaluationComponentId}/students/{studentId}")
    public ResponseEntity<Void> deleteGradeEntry(@PathVariable Long classId, @PathVariable Long gradeEvaluationComponentId,
                                                  @PathVariable Long studentId,
                                                  @AuthenticationPrincipal AuthenticatedUser actor) {
        gradeService.deleteGradeEntry(classId, gradeEvaluationComponentId, studentId, actor.userId());
        return ResponseEntity.noContent().build();
    }

    /** UC-19 Main Flow bước 4 (V44): Giáo viên gửi duyệt — DRAFT/REJECTED -> SUBMITTED, chờ Quản lý điểm trường duyệt qua UC-20. */
    @PostMapping("/api/grades/submit")
    public ResponseEntity<List<GradeEntryResponse>> submitGradesForApproval(@Valid @RequestBody SubmitGradesRequest request,
                                                                              @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(gradeService.submitGradesForApproval(request, actor.userId()));
    }

    // ---- UC-53: Overall/Level theo (kỳ học, Giữa/Cuối kỳ) (TEACHER + HEAD_ACADEMIC/SITE_MANAGER hỗ trợ) ----

    @PostMapping("/api/classes/{classId}/grades/students/{studentId}/setups/{setupId}/result")
    public ResponseEntity<GradeEvaluationResultResponse> enterEvaluationResult(@PathVariable Long classId,
                                                                       @PathVariable Long studentId,
                                                                       @PathVariable Long setupId,
                                                                       @Valid @RequestBody EnterGradeEvaluationResultRequest request,
                                                                       @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(gradeService.enterEvaluationResult(classId, studentId, setupId, request, actor.userId()));
    }

    @GetMapping("/api/classes/{classId}/grade-component-setups/{setupId}/results")
    public ResponseEntity<List<GradeEvaluationResultResponse>> listEvaluationResults(@PathVariable Long classId,
                                                                             @PathVariable Long setupId) {
        return ResponseEntity.ok(gradeService.listEvaluationResults(classId, setupId));
    }

    /** UC-53 (xoá điểm tổng kết kỳ nháp, bổ sung ngoài SDD gốc, đã xác nhận với người dùng) — chỉ xoá được bản ghi DRAFT (hoặc academic.grade.edit.override). */
    @DeleteMapping("/api/classes/{classId}/grades/students/{studentId}/setups/{setupId}/result")
    public ResponseEntity<Void> deleteEvaluationResult(@PathVariable Long classId, @PathVariable Long studentId,
                                                    @PathVariable Long setupId,
                                                    @AuthenticationPrincipal AuthenticatedUser actor) {
        gradeService.deleteEvaluationResult(classId, studentId, setupId, actor.userId());
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
