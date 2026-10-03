package vn.com.pps.education.academic.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.com.pps.education.academic.dto.AcademicDashboardResponse;
import vn.com.pps.education.security.AuthenticatedUser;
import vn.com.pps.education.academic.service.AcademicDashboardService;

/**
 * Dashboard Trưởng phòng đào tạo (V203 — bổ sung ngoài SDD gốc, xác nhận với người dùng 2026-09-30)
 * — xem Javadoc AcademicDashboardService. Gate academic.class.view vì toàn bộ số liệu là dữ liệu lớp
 * học (không cấp cho vai trò không xem được lớp chỉ vì có dashboard.view).
 */
@RestController
public class AcademicDashboardController {

    private final AcademicDashboardService academicDashboardService;

    public AcademicDashboardController(AcademicDashboardService academicDashboardService) {
        this.academicDashboardService = academicDashboardService;
    }

    @PreAuthorize("hasPermission(null, 'academic.class.view')")
    @GetMapping("/api/dashboard/academic-overview")
    public ResponseEntity<AcademicDashboardResponse> getOverview(@RequestParam(required = false) Long siteId,
                                                                 @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(academicDashboardService.getOverview(siteId, actor.userId()));
    }
}
