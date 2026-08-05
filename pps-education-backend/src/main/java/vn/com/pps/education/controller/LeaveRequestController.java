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
import vn.com.pps.education.dto.ClassSessionResponse;
import vn.com.pps.education.dto.CreateLeaveRequestRequest;
import vn.com.pps.education.dto.DecideLeaveRequestRequest;
import vn.com.pps.education.dto.LeaveRequestApprovalResponse;
import vn.com.pps.education.dto.LeaveRequestResponse;
import vn.com.pps.education.security.AuthenticatedUser;
import vn.com.pps.education.service.LeaveRequestService;

import java.time.LocalDate;
import java.util.List;

/**
 * UC-10: Nộp đơn từ + UC-11: Duyệt đơn từ (FR-HRM-03). Tự phục vụ (nộp cho
 * chính mình) + role-based (duyệt theo vai trò/thẩm quyền) — không cần
 * permission code riêng (không có trong Precondition của cả 2 UC).
 */
@RestController
@RequestMapping("/api/leave-requests")
public class LeaveRequestController {

    private final LeaveRequestService leaveRequestService;

    public LeaveRequestController(LeaveRequestService leaveRequestService) {
        this.leaveRequestService = leaveRequestService;
    }

    @PostMapping
    public ResponseEntity<LeaveRequestResponse> submit(@Valid @RequestBody CreateLeaveRequestRequest request,
                                                          @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(leaveRequestService.submit(actor.userId(), request));
    }

    /** UC-10 bước 3: buổi dạy của người gọi trong khoảng nghỉ dự kiến, để chọn giáo viên dạy thay trước khi nộp đơn. */
    @GetMapping("/teaching-sessions")
    public ResponseEntity<List<ClassSessionResponse>> findTeachingSessions(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(leaveRequestService.findTeachingSessions(actor.userId(), startDate, endDate));
    }

    @GetMapping("/pending-for-me")
    public ResponseEntity<List<LeaveRequestResponse>> listPendingForApprover(@AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(leaveRequestService.listPendingForApprover(actor.userId()));
    }

    /** Self-service: đơn từ đã nộp của chính người gọi. */
    @GetMapping("/mine")
    public ResponseEntity<List<LeaveRequestResponse>> listMine(@AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(leaveRequestService.listMine(actor.userId()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<LeaveRequestResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(leaveRequestService.getById(id));
    }

    @GetMapping("/{id}/approvals")
    public ResponseEntity<List<LeaveRequestApprovalResponse>> listApprovals(@PathVariable Long id) {
        return ResponseEntity.ok(leaveRequestService.listApprovals(id));
    }

    @PostMapping("/{id}/decision")
    public ResponseEntity<LeaveRequestResponse> decide(@PathVariable Long id,
                                                          @Valid @RequestBody DecideLeaveRequestRequest request,
                                                          @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(leaveRequestService.decide(actor.userId(), id, request));
    }
}
