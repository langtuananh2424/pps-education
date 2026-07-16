package vn.com.pps.education.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.com.pps.education.dto.GradeEntryResponse;
import vn.com.pps.education.dto.PartnerAttendanceSummaryResponse;
import vn.com.pps.education.dto.PartnerSiteResponse;
import vn.com.pps.education.dto.StudentCommentResponse;
import vn.com.pps.education.dto.TeachingPlanResponse;
import vn.com.pps.education.security.AuthenticatedUser;
import vn.com.pps.education.service.PartnerPortalService;

import java.util.List;

/** UC-29: Xem báo cáo Portal trường liên kết (FR-LMS-08) — xem Javadoc PartnerPortalService. */
@RestController
@RequestMapping("/api/portal/partner")
public class PartnerPortalController {

    private final PartnerPortalService partnerPortalService;

    public PartnerPortalController(PartnerPortalService partnerPortalService) {
        this.partnerPortalService = partnerPortalService;
    }

    @GetMapping("/site")
    public ResponseEntity<PartnerSiteResponse> getMySite(@AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(partnerPortalService.getMySite(actor.userId()));
    }

    @GetMapping("/attendance-summary")
    public ResponseEntity<List<PartnerAttendanceSummaryResponse>> getAttendanceSummary(@AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(partnerPortalService.getAttendanceSummary(actor.userId()));
    }

    @GetMapping("/grades")
    public ResponseEntity<List<GradeEntryResponse>> getApprovedGrades(@AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(partnerPortalService.getApprovedGrades(actor.userId()));
    }

    @GetMapping("/teaching-plans")
    public ResponseEntity<List<TeachingPlanResponse>> getTeachingPlans(@AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(partnerPortalService.getTeachingPlans(actor.userId()));
    }

    @GetMapping("/comments")
    public ResponseEntity<List<StudentCommentResponse>> getApprovedComments(@AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(partnerPortalService.getApprovedComments(actor.userId()));
    }
}
