package vn.com.pps.education.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.com.pps.education.dto.CreateLeaveRequestRequest;
import vn.com.pps.education.dto.DecideLeaveRequestRequest;
import vn.com.pps.education.dto.LeaveRequestResponse;
import vn.com.pps.education.security.AuthenticatedUser;
import vn.com.pps.education.service.LeaveRequestService;

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

    @GetMapping("/pending-for-me")
    public ResponseEntity<List<LeaveRequestResponse>> listPendingForApprover(@AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(leaveRequestService.listPendingForApprover(actor.userId()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<LeaveRequestResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(leaveRequestService.getById(id));
    }

    @PostMapping("/{id}/decision")
    public ResponseEntity<LeaveRequestResponse> decide(@PathVariable Long id,
                                                          @Valid @RequestBody DecideLeaveRequestRequest request,
                                                          @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(leaveRequestService.decide(actor.userId(), id, request));
    }
}
