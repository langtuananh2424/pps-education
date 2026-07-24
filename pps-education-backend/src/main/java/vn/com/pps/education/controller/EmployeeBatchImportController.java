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
import org.springframework.web.multipart.MultipartFile;
import vn.com.pps.education.common.ExcelHttpResponses;
import vn.com.pps.education.dto.AccountExportRequest;
import vn.com.pps.education.dto.EmployeeBatchImportResponse;
import vn.com.pps.education.security.AuthenticatedUser;
import vn.com.pps.education.service.EmployeeBatchImportService;

/**
 * UC-51: Nhập nhân sự theo lô (FR-HRM-05) — xem Javadoc
 * EmployeeBatchImportService. Tái dùng permission hrm.manage đã có sẵn
 * (khớp EmployeeController — UC-08 tạo hồ sơ nhân sự) vì UC-51 cũng tạo
 * hồ sơ nhân sự, chỉ khác là hàng loạt qua Excel thay vì từng dòng.
 *
 * @PreAuthorize đặt ở class-level ngay từ đầu — rút kinh nghiệm lỗ hổng
 * thiếu quyền từng xảy ra ở StudentBatchImportController.
 */
@RestController
@PreAuthorize("hasPermission(null, 'hrm.manage')")
public class EmployeeBatchImportController {

    private final EmployeeBatchImportService employeeBatchImportService;

    public EmployeeBatchImportController(EmployeeBatchImportService employeeBatchImportService) {
        this.employeeBatchImportService = employeeBatchImportService;
    }

    @PostMapping(value = "/api/employee-imports", consumes = "multipart/form-data")
    public ResponseEntity<EmployeeBatchImportResponse> importEmployees(@RequestParam("file") MultipartFile file,
                                                                          @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(employeeBatchImportService.importEmployees(file, actor.userId()));
    }

    @GetMapping("/api/employee-imports/{id}")
    public ResponseEntity<EmployeeBatchImportResponse> getJob(@PathVariable Long id) {
        return ResponseEntity.ok(employeeBatchImportService.getJob(id));
    }

    /** File mẫu nhập nhân sự theo lô (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-07-24). */
    @GetMapping("/api/employee-imports/template")
    public ResponseEntity<byte[]> downloadTemplate() {
        return ExcelHttpResponses.attachment(employeeBatchImportService.buildTemplate(), "mau-nhap-nhan-su.xlsx");
    }

    /**
     * Xuất Excel danh sách tài khoản nhân sự vừa tạo (bổ sung ngoài SDD
     * gốc, đã xác nhận với người dùng 2026-07-24) — xem Javadoc
     * StudentBatchImportController.exportAccounts().
     */
    @PostMapping("/api/employee-imports/accounts-export")
    public ResponseEntity<byte[]> exportAccounts(@Valid @RequestBody AccountExportRequest request) {
        return ExcelHttpResponses.attachment(
                employeeBatchImportService.buildAccountsExport(request), "tai-khoan-nhan-su.xlsx");
    }
}
