package vn.com.pps.education.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.com.pps.education.dto.BulkCreateClassSessionRequest;
import vn.com.pps.education.dto.BulkCreateClassSessionResponse;
import vn.com.pps.education.dto.CancelClassSessionRequest;
import vn.com.pps.education.dto.ClassSessionResponse;
import vn.com.pps.education.dto.CreateClassSessionRequest;
import vn.com.pps.education.dto.RescheduleClassSessionRequest;
import vn.com.pps.education.dto.SessionPeriodResponse;
import vn.com.pps.education.security.AuthenticatedUser;
import vn.com.pps.education.service.ClassSessionService;

import java.util.List;

/** UC-48: Xếp lịch buổi học (FR-ACA-05) — xem Javadoc ClassSessionService. */
@RestController
@RequestMapping("/api/classes/{classId}/sessions")
public class ClassSessionController {

    private final ClassSessionService classSessionService;

    public ClassSessionController(ClassSessionService classSessionService) {
        this.classSessionService = classSessionService;
    }

    @GetMapping
    public ResponseEntity<List<ClassSessionResponse>> listSessions(@PathVariable Long classId,
                                                                     @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(classSessionService.listSessions(classId, actor.userId()));
    }

    @PreAuthorize("hasPermission(null, 'academic.class.manage')")
    @PostMapping
    public ResponseEntity<ClassSessionResponse> createSession(@PathVariable Long classId,
                                                                  @Valid @RequestBody CreateClassSessionRequest request,
                                                                  @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(classSessionService.createSession(classId, request, actor.userId()));
    }

    /** UC-56: Sinh lịch học hàng loạt theo mẫu lặp (FR-ACA-05, bổ sung ngoài SDD gốc, đã xác nhận với người dùng). */
    @PreAuthorize("hasPermission(null, 'academic.class.manage')")
    @PostMapping("/bulk")
    public ResponseEntity<BulkCreateClassSessionResponse> bulkCreateSessions(@PathVariable Long classId,
                                                                              @Valid @RequestBody BulkCreateClassSessionRequest request,
                                                                              @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(classSessionService.bulkCreateSessions(classId, request, actor.userId()));
    }

    @GetMapping("/{classSessionId}/periods")
    public ResponseEntity<List<SessionPeriodResponse>> listPeriods(@PathVariable Long classId, @PathVariable Long classSessionId,
                                                                    @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(classSessionService.listPeriods(classSessionId, actor.userId()));
    }

    @PreAuthorize("hasPermission(null, 'academic.class.manage')")
    @PostMapping("/{classSessionId}/cancel")
    public ResponseEntity<ClassSessionResponse> cancelSession(@PathVariable Long classId,
                                                                @PathVariable Long classSessionId,
                                                                @Valid @RequestBody CancelClassSessionRequest request,
                                                                @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(classSessionService.cancelSession(classId, classSessionId, request, actor.userId()));
    }

    @PreAuthorize("hasPermission(null, 'academic.class.manage')")
    @PostMapping("/{classSessionId}/reschedule")
    public ResponseEntity<ClassSessionResponse> rescheduleSession(@PathVariable Long classId,
                                                                    @PathVariable Long classSessionId,
                                                                    @Valid @RequestBody RescheduleClassSessionRequest request,
                                                                    @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(classSessionService.rescheduleSession(classId, classSessionId, request, actor.userId()));
    }
}
