package vn.com.pps.education.controller;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.com.pps.education.dto.ClassSessionResponse;
import vn.com.pps.education.security.AuthenticatedUser;
import vn.com.pps.education.service.ClassSessionService;

import java.time.LocalDate;
import java.util.List;

/**
 * UC-58: "Lịch của tôi" — Giáo viên xem lịch dạy tổng hợp qua MỌI lớp
 * (FR-ACA-05, bổ sung ngoài SDD gốc, đã xác nhận với người dùng). Self-
 * service — chỉ cần đã đăng nhập, không cần permission đặc biệt (giống
 * pattern GET /api/auth/me).
 */
@RestController
@RequestMapping("/api/teachers/me")
public class TeacherScheduleController {

    private final ClassSessionService classSessionService;

    public TeacherScheduleController(ClassSessionService classSessionService) {
        this.classSessionService = classSessionService;
    }

    @GetMapping("/sessions")
    public ResponseEntity<List<ClassSessionResponse>> listMySessions(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(classSessionService.listMySessions(actor.userId(), fromDate, toDate));
    }
}
