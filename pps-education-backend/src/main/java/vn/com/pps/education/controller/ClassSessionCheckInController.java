package vn.com.pps.education.controller;

import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.com.pps.education.dto.ClassSessionCheckInRequest;
import vn.com.pps.education.dto.ClassSessionCheckInResponse;
import vn.com.pps.education.dto.ClassSessionCheckInStatusResponse;
import vn.com.pps.education.security.AuthenticatedUser;
import vn.com.pps.education.service.ClassSessionCheckInService;
import vn.com.pps.education.service.ClassSessionService;

import java.time.LocalDate;
import java.util.List;

/**
 * UC-71: Nhận lớp (bổ sung ngoài SDD/SRS gốc, đã xác nhận với người dùng).
 * Tự phục vụ — Giáo viên nhận lớp/xem trạng thái của chính mình, không cần
 * permission riêng ngoài đăng nhập (giống pattern AttendanceController/
 * TeacherScheduleController). Xem docs/uc/phan-he-06-hoc-thuat.md.
 */
@RestController
@RequestMapping("/api/class-sessions")
public class ClassSessionCheckInController {

    private final ClassSessionCheckInService classSessionCheckInService;
    private final ClassSessionService classSessionService;

    public ClassSessionCheckInController(ClassSessionCheckInService classSessionCheckInService,
                                          ClassSessionService classSessionService) {
        this.classSessionCheckInService = classSessionCheckInService;
        this.classSessionService = classSessionService;
    }

    @PostMapping("/{id}/check-in")
    public ResponseEntity<ClassSessionCheckInResponse> checkIn(@PathVariable Long id,
                                                                 @Valid @RequestBody ClassSessionCheckInRequest request,
                                                                 @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(classSessionCheckInService.checkIn(id, actor.userId(), request));
    }

    /** Trạng thái nhận lớp (tính ra, xem ClassSessionCheckInService#listEffectiveStatus) của chính GV trong khoảng ngày. */
    @GetMapping("/my-check-in-status")
    public ResponseEntity<List<ClassSessionCheckInStatusResponse>> getMyCheckInStatus(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @AuthenticationPrincipal AuthenticatedUser actor) {
        var sessions = classSessionService.listMySessions(actor.userId(), from, to);
        return ResponseEntity.ok(classSessionCheckInService.listEffectiveStatus(sessions));
    }
}
