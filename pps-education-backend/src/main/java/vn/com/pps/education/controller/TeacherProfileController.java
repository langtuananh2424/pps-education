package vn.com.pps.education.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.com.pps.education.dto.TeacherProfileDetailResponse;
import vn.com.pps.education.dto.TeacherProfileSummaryResponse;
import vn.com.pps.education.security.AuthenticatedUser;
import vn.com.pps.education.service.TeacherProfileService;

import java.util.List;

/**
 * Trang "Hồ sơ giáo viên" (V203 — bổ sung ngoài SDD gốc, xác nhận với người dùng 2026-09-30) —
 * xem Javadoc TeacherProfileService. Lịch dạy của giáo viên dùng lại
 * GET /api/employees/{id}/teaching-sessions (quyền hrm.employee-schedule.view).
 */
@RestController
@RequestMapping("/api/teacher-profiles")
public class TeacherProfileController {

    private final TeacherProfileService teacherProfileService;

    public TeacherProfileController(TeacherProfileService teacherProfileService) {
        this.teacherProfileService = teacherProfileService;
    }

    @PreAuthorize("hasPermission(null, 'hrm.teacher.view')")
    @GetMapping
    public ResponseEntity<List<TeacherProfileSummaryResponse>> search(@RequestParam(required = false) String query,
                                                                      @RequestParam(required = false) Long siteId,
                                                                      @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(teacherProfileService.search(query, siteId, actor.userId()));
    }

    @PreAuthorize("hasPermission(null, 'hrm.teacher.view')")
    @GetMapping("/{employeeId}")
    public ResponseEntity<TeacherProfileDetailResponse> getDetail(@PathVariable Long employeeId,
                                                                  @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(teacherProfileService.getDetail(employeeId, actor.userId()));
    }
}
