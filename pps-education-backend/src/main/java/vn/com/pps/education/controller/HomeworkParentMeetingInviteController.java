package vn.com.pps.education.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import vn.com.pps.education.dto.DecideHomeworkParentMeetingInvitesRequest;
import vn.com.pps.education.dto.HomeworkParentMeetingInviteResponse;
import vn.com.pps.education.security.AuthenticatedUser;
import vn.com.pps.education.service.HomeworkParentMeetingInviteService;

import java.util.List;

/**
 * Duyệt "Thư mời phụ huynh tới làm việc" trước khi gửi xuống Phụ huynh —
 * bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-12. Không có
 * {@code @PreAuthorize} riêng — quyền được tính qua quan hệ dữ liệu
 * (site_managers, giống PartnerFeedbackController "Ý kiến phản hồi"),
 * không qua 1 permission cụ thể nào.
 */
@RestController
public class HomeworkParentMeetingInviteController {

    private final HomeworkParentMeetingInviteService inviteService;

    public HomeworkParentMeetingInviteController(HomeworkParentMeetingInviteService inviteService) {
        this.inviteService = inviteService;
    }

    @GetMapping("/api/homework-meeting-invites/pending")
    public ResponseEntity<List<HomeworkParentMeetingInviteResponse>> listPendingForSite(
            @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(inviteService.listPendingForSite(actor.userId()));
    }

    @PostMapping("/api/homework-meeting-invites/decision")
    public ResponseEntity<List<HomeworkParentMeetingInviteResponse>> decide(
            @Valid @RequestBody DecideHomeworkParentMeetingInvitesRequest request,
            @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(inviteService.decide(request.inviteIds(), request.decision(), request.comment(), actor.userId()));
    }
}
