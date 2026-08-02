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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import vn.com.pps.education.common.ExcelHttpResponses;
import vn.com.pps.education.dto.AssignTeacherRequest;
import vn.com.pps.education.dto.ClassEnrollmentBatchImportResponse;
import vn.com.pps.education.dto.ClassEnrollmentResponse;
import vn.com.pps.education.dto.ClassResponse;
import vn.com.pps.education.dto.ClassTeacherResponse;
import vn.com.pps.education.dto.CreateClassRequest;
import vn.com.pps.education.dto.EndTeacherAssignmentRequest;
import vn.com.pps.education.dto.EnrollStudentRequest;
import vn.com.pps.education.dto.UpdateClassRequest;
import vn.com.pps.education.dto.WithdrawEnrollmentRequest;
import vn.com.pps.education.security.AuthenticatedUser;
import vn.com.pps.education.service.ClassEnrollmentBatchImportService;
import vn.com.pps.education.service.ClassService;

import java.util.List;

/** UC-18: Xếp lớp & gán khóa học (FR-ACA-02). */
@RestController
@RequestMapping("/api/classes")
public class ClassController {

    private final ClassService classService;
    private final ClassEnrollmentBatchImportService classEnrollmentBatchImportService;

    public ClassController(ClassService classService, ClassEnrollmentBatchImportService classEnrollmentBatchImportService) {
        this.classService = classService;
        this.classEnrollmentBatchImportService = classEnrollmentBatchImportService;
    }

    /**
     * Dropdown FE: chọn trường (siteId) -> hiển thị lớp của trường đó; lọc
     * thêm theo chương trình (curriculumId/classCategory). Giáo viên (không
     * có quyền academic.class.manage) chỉ thấy lớp thuộc (các) site được
     * gán qua site_teachers — xem Javadoc ClassService.search.
     */
    @GetMapping
    public ResponseEntity<List<ClassResponse>> search(@RequestParam(required = false) String query,
                                                       @RequestParam(required = false) Long siteId,
                                                       @RequestParam(required = false) Long curriculumId,
                                                       @RequestParam(required = false) String classCategory,
                                                       @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(classService.search(query, siteId, curriculumId, classCategory, actor.userId()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ClassResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(classService.getById(id));
    }

    @PreAuthorize("hasPermission(null, 'academic.class.manage')")
    @PostMapping
    public ResponseEntity<ClassResponse> create(@Valid @RequestBody CreateClassRequest request,
                                                   @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(classService.create(request, actor.userId()));
    }

    @PreAuthorize("hasPermission(null, 'academic.class.manage')")
    @PutMapping("/{id}")
    public ResponseEntity<ClassResponse> update(@PathVariable Long id,
                                                   @Valid @RequestBody UpdateClassRequest request,
                                                   @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(classService.update(id, request, actor.userId()));
    }

    @GetMapping("/{id}/teachers")
    public ResponseEntity<List<ClassTeacherResponse>> listTeachers(@PathVariable Long id) {
        return ResponseEntity.ok(classService.listTeachers(id));
    }

    @PreAuthorize("hasPermission(null, 'academic.class.manage')")
    @PostMapping("/{id}/teachers")
    public ResponseEntity<ClassTeacherResponse> assignTeacher(@PathVariable Long id,
                                                                  @Valid @RequestBody AssignTeacherRequest request,
                                                                  @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(classService.assignTeacher(id, request, actor.userId()));
    }

    /** Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-07-31 — kết thúc phụ trách của 1 giáo viên với lớp. */
    @PreAuthorize("hasPermission(null, 'academic.class.manage')")
    @PutMapping("/{id}/teachers/{classTeacherId}/end")
    public ResponseEntity<ClassTeacherResponse> endTeacherAssignment(@PathVariable Long id,
                                                                        @PathVariable Long classTeacherId,
                                                                        @Valid @RequestBody EndTeacherAssignmentRequest request,
                                                                        @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(classService.endTeacherAssignment(id, classTeacherId, request, actor.userId()));
    }

    @GetMapping("/{id}/enrollments")
    public ResponseEntity<List<ClassEnrollmentResponse>> listEnrollments(@PathVariable Long id) {
        return ResponseEntity.ok(classService.listEnrollments(id));
    }

    @PreAuthorize("hasPermission(null, 'academic.class.manage')")
    @PostMapping("/{id}/enrollments")
    public ResponseEntity<ClassEnrollmentResponse> enroll(@PathVariable Long id,
                                                              @Valid @RequestBody EnrollStudentRequest request,
                                                              @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(classService.enroll(id, request, actor.userId()));
    }

    @PreAuthorize("hasPermission(null, 'academic.class.manage')")
    @PostMapping("/{id}/enrollments/{enrollmentId}/withdraw")
    public ResponseEntity<ClassEnrollmentResponse> withdraw(@PathVariable Long id,
                                                                @PathVariable Long enrollmentId,
                                                                @Valid @RequestBody WithdrawEnrollmentRequest request,
                                                                @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(classService.withdraw(id, enrollmentId, request, actor.userId()));
    }

    /** UC-65: ghi danh học sinh (đã tồn tại sẵn) theo lô qua Excel — bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-07-31. */
    @PreAuthorize("hasPermission(null, 'academic.class.manage')")
    @PostMapping(value = "/{id}/enrollments/import", consumes = "multipart/form-data")
    public ResponseEntity<ClassEnrollmentBatchImportResponse> importEnrollments(@PathVariable Long id,
                                                                                 @RequestParam("file") MultipartFile file,
                                                                                 @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(classEnrollmentBatchImportService.importEnrollments(id, file, actor.userId()));
    }

    /** File mẫu ghi danh học sinh theo lô (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-07-31). */
    @PreAuthorize("hasPermission(null, 'academic.class.manage')")
    @GetMapping("/{id}/enrollments/import-template")
    public ResponseEntity<byte[]> downloadEnrollmentImportTemplate(@PathVariable Long id) {
        return ExcelHttpResponses.attachment(classEnrollmentBatchImportService.buildTemplate(), "mau-ghi-danh-hoc-sinh.xlsx");
    }
}
