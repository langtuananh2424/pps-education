package vn.com.pps.education.controller;

import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.com.pps.education.dto.ChangeHistoryItemResponse;
import vn.com.pps.education.security.AuthenticatedUser;
import vn.com.pps.education.service.ChangeHistoryService;

import java.time.LocalDate;

/**
 * Trang "Lịch sử thay đổi dữ liệu" (V203 — bổ sung ngoài SDD gốc, xác nhận với người dùng
 * 2026-09-30) — xem Javadoc ChangeHistoryService.
 */
@RestController
public class ChangeHistoryController {

    private final ChangeHistoryService changeHistoryService;

    public ChangeHistoryController(ChangeHistoryService changeHistoryService) {
        this.changeHistoryService = changeHistoryService;
    }

    @PreAuthorize("hasPermission(null, 'academic.change-history.view')")
    @GetMapping("/api/change-history")
    public ResponseEntity<Page<ChangeHistoryItemResponse>> search(
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) Long siteId,
            @RequestParam(required = false) Long classId,
            @RequestParam(required = false) Long studentId,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(changeHistoryService.search(entityType, fromDate, toDate, siteId, classId, studentId,
                keyword, page, size, actor.userId()));
    }
}
