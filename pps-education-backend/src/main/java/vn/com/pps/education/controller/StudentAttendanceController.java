package vn.com.pps.education.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.com.pps.education.dto.AttendanceMarkResponse;
import vn.com.pps.education.dto.AttendanceSessionResponse;
import vn.com.pps.education.dto.MarkAttendanceRequest;
import vn.com.pps.education.dto.UpdatePeriodMarkRequest;
import vn.com.pps.education.security.AuthenticatedUser;
import vn.com.pps.education.service.StudentAttendanceService;

/** UC-15: Điểm danh học sinh (FR-STU-03) — xem Javadoc StudentAttendanceService. */
@RestController
@RequestMapping("/api/class-sessions/{classSessionId}/attendance")
public class StudentAttendanceController {

    private final StudentAttendanceService studentAttendanceService;

    public StudentAttendanceController(StudentAttendanceService studentAttendanceService) {
        this.studentAttendanceService = studentAttendanceService;
    }

    @GetMapping
    public ResponseEntity<AttendanceSessionResponse> getAttendanceSession(@PathVariable Long classSessionId) {
        return ResponseEntity.ok(studentAttendanceService.getAttendanceSession(classSessionId));
    }

    @PostMapping
    public ResponseEntity<AttendanceSessionResponse> markAttendance(@PathVariable Long classSessionId,
                                                                        @Valid @RequestBody MarkAttendanceRequest request,
                                                                        @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(studentAttendanceService.markAttendance(classSessionId, request, actor.userId()));
    }

    @PutMapping("/students/{studentId}/periods/{sessionPeriodId}")
    public ResponseEntity<AttendanceMarkResponse> updatePeriodMark(@PathVariable Long classSessionId,
                                                                       @PathVariable Long studentId,
                                                                       @PathVariable Long sessionPeriodId,
                                                                       @Valid @RequestBody UpdatePeriodMarkRequest request,
                                                                       @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(studentAttendanceService.updatePeriodMark(classSessionId, studentId, sessionPeriodId, request, actor.userId()));
    }

    @PostMapping("/submit")
    public ResponseEntity<AttendanceSessionResponse> submitAttendance(@PathVariable Long classSessionId,
                                                                          @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(studentAttendanceService.submitAttendance(classSessionId, actor.userId()));
    }
}
