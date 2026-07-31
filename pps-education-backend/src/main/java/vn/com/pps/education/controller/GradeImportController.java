package vn.com.pps.education.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import vn.com.pps.education.common.ExcelHttpResponses;
import vn.com.pps.education.dto.GradeImportResponse;
import vn.com.pps.education.security.AuthenticatedUser;
import vn.com.pps.education.service.GradeImportService;

/**
 * UC-53: Nhập điểm thi qua Excel (FR-ACA-03) — xem Javadoc
 * GradeImportService. Quyền: Giáo viên được phân công giảng dạy lớp, HOẶC
 * Trưởng phòng đào tạo (academic.grade.manage), HOẶC Quản lý điểm trường
 * phụ trách site của lớp (GradeService#requireCanEnterGrades, row-level
 * check trong service — giống enterGrade UC-19), không phải
 * @PreAuthorize role-hardcode.
 */
@RestController
public class GradeImportController {

    private final GradeImportService gradeImportService;

    public GradeImportController(GradeImportService gradeImportService) {
        this.gradeImportService = gradeImportService;
    }

    @PostMapping(value = "/api/classes/{classId}/grade-periods/{gradePeriodId}/grades/import",
            consumes = "multipart/form-data")
    public ResponseEntity<GradeImportResponse> importGrades(@PathVariable Long classId,
                                                            @PathVariable Long gradePeriodId,
                                                            @RequestParam("file") MultipartFile file,
                                                            @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(gradeImportService.importGrades(classId, gradePeriodId, file, actor.userId()));
    }

    @GetMapping("/api/grade-imports/{id}")
    public ResponseEntity<GradeImportResponse> getJob(@PathVariable Long id) {
        return ResponseEntity.ok(gradeImportService.getJob(id));
    }

    /**
     * File mẫu để nhập điểm qua Excel (bổ sung ngoài SDD gốc, đã xác nhận
     * với người dùng 2026-07-24) — điền sẵn học sinh của lớp + cột điểm để
     * trống. Quyền: giống importGrades (xem Javadoc GradeImportService).
     */
    @GetMapping("/api/classes/{classId}/grade-periods/{gradePeriodId}/grades/import-template")
    public ResponseEntity<byte[]> downloadTemplate(@PathVariable Long classId,
                                                    @PathVariable Long gradePeriodId,
                                                    @AuthenticationPrincipal AuthenticatedUser actor) {
        byte[] content = gradeImportService.buildTemplate(classId, gradePeriodId, actor.userId());
        return ExcelHttpResponses.attachment(content, "mau-nhap-diem-lop-" + classId + ".xlsx");
    }
}
