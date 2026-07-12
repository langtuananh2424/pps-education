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
import vn.com.pps.education.dto.ClassSessionResponse;
import vn.com.pps.education.dto.CreateClassSessionRequest;
import vn.com.pps.education.dto.SessionPeriodResponse;
import vn.com.pps.education.security.AuthenticatedUser;
import vn.com.pps.education.service.ClassSessionService;

import java.util.List;

/** Xếp lịch buổi học (class_sessions/session_periods) — xem Javadoc ClassSessionService. */
@RestController
@RequestMapping("/api/classes/{classId}/sessions")
public class ClassSessionController {

    private final ClassSessionService classSessionService;

    public ClassSessionController(ClassSessionService classSessionService) {
        this.classSessionService = classSessionService;
    }

    @GetMapping
    public ResponseEntity<List<ClassSessionResponse>> listSessions(@PathVariable Long classId) {
        return ResponseEntity.ok(classSessionService.listSessions(classId));
    }

    @PostMapping
    public ResponseEntity<ClassSessionResponse> createSession(@PathVariable Long classId,
                                                                  @Valid @RequestBody CreateClassSessionRequest request,
                                                                  @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(classSessionService.createSession(classId, request, actor.userId()));
    }

    @GetMapping("/{classSessionId}/periods")
    public ResponseEntity<List<SessionPeriodResponse>> listPeriods(@PathVariable Long classId, @PathVariable Long classSessionId) {
        return ResponseEntity.ok(classSessionService.listPeriods(classSessionId));
    }
}
