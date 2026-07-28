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
import vn.com.pps.education.dto.ParentBatchImportResponse;
import vn.com.pps.education.security.AuthenticatedUser;
import vn.com.pps.education.service.ParentBatchImportService;

/**
 * UC-50: Nhập phụ huynh theo lô, liên kết học sinh có sẵn (FR-STU-04) —
 * xem Javadoc ParentBatchImportService. Dùng permission riêng
 * student.parent.import (V51, tách từ student.parent.manage — bổ sung
 * ngoài SDD gốc, đã xác nhận với người dùng 2026-07-24), khớp quy ước
 * student.profile.import/hrm.employee.import.
 *
 * @PreAuthorize đặt ở class-level ngay từ đầu — rút kinh nghiệm lỗ hổng
 * thiếu quyền từng xảy ra ở StudentBatchImportController.
 */
@RestController
@PreAuthorize("hasPermission(null, 'student.parent.import')")
public class ParentBatchImportController {

    private final ParentBatchImportService parentBatchImportService;

    public ParentBatchImportController(ParentBatchImportService parentBatchImportService) {
        this.parentBatchImportService = parentBatchImportService;
    }

    @PostMapping(value = "/api/parent-imports", consumes = "multipart/form-data")
    public ResponseEntity<ParentBatchImportResponse> importParents(@RequestParam("file") MultipartFile file,
                                                                       @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(parentBatchImportService.importParents(file, actor.userId()));
    }

    @GetMapping("/api/parent-imports/{id}")
    public ResponseEntity<ParentBatchImportResponse> getJob(@PathVariable Long id) {
        return ResponseEntity.ok(parentBatchImportService.getJob(id));
    }

    /** File mẫu nhập phụ huynh theo lô (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-07-24). */
    @GetMapping("/api/parent-imports/template")
    public ResponseEntity<byte[]> downloadTemplate() {
        return ExcelHttpResponses.attachment(parentBatchImportService.buildTemplate(), "mau-nhap-phu-huynh.xlsx");
    }

    /**
     * Xuất Excel danh sách tài khoản phụ huynh vừa tạo (bổ sung ngoài SDD
     * gốc, đã xác nhận với người dùng 2026-07-24) — xem Javadoc
     * StudentBatchImportController.exportAccounts().
     */
    @PostMapping("/api/parent-imports/accounts-export")
    public ResponseEntity<byte[]> exportAccounts(@Valid @RequestBody AccountExportRequest request) {
        return ExcelHttpResponses.attachment(
                parentBatchImportService.buildAccountsExport(request), "tai-khoan-phu-huynh.xlsx");
    }
}
