package vn.com.pps.education.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import vn.com.pps.education.dto.DecideStudentAttitudeEscalationsRequest;
import vn.com.pps.education.dto.StudentAttitudeEscalationResponse;
import vn.com.pps.education.security.AuthenticatedUser;
import vn.com.pps.education.service.StudentAttitudeEscalationService;

import java.util.List;

/**
 * Duyệt cảnh báo thái độ học tập liên tục 3 buổi trước khi gửi xuống Phụ
 * huynh — bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-12.
 * Không có {@code @PreAuthorize} riêng — quyền được tính qua quan hệ dữ
 * liệu (site_managers, giống HomeworkParentMeetingInviteController).
 */
@RestController
public class StudentAttitudeEscalationController {

    private final StudentAttitudeEscalationService escalationService;

    public StudentAttitudeEscalationController(StudentAttitudeEscalationService escalationService) {
        this.escalationService = escalationService;
    }

    @GetMapping("/api/student-attitude-escalations/pending")
    public ResponseEntity<List<StudentAttitudeEscalationResponse>> listPendingForSite(
            @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(escalationService.listPendingForSite(actor.userId()));
    }

    @PostMapping("/api/student-attitude-escalations/decision")
    public ResponseEntity<List<StudentAttitudeEscalationResponse>> decide(
            @Valid @RequestBody DecideStudentAttitudeEscalationsRequest request,
            @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(escalationService.decide(request.escalationIds(), request.decision(), request.comment(), actor.userId()));
    }
}
