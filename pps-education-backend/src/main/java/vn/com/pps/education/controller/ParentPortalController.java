package vn.com.pps.education.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.com.pps.education.dto.AttendanceMarkResponse;
import vn.com.pps.education.dto.ChildResponse;
import vn.com.pps.education.dto.ClassSessionResponse;
import vn.com.pps.education.dto.GradeEntryResponse;
import vn.com.pps.education.dto.GradeEvaluationResultResponse;
import vn.com.pps.education.dto.HomeworkProgressResponse;
import vn.com.pps.education.dto.StudentCommentResponse;
import vn.com.pps.education.security.AuthenticatedUser;
import vn.com.pps.education.service.ParentPortalService;

import java.util.List;

/** UC-25: Xem Portal Phụ huynh (FR-LMS-03, FR-LMS-07) — xem Javadoc ParentPortalService. */
@RestController
@RequestMapping("/api/portal/parent")
public class ParentPortalController {

    private final ParentPortalService parentPortalService;

    public ParentPortalController(ParentPortalService parentPortalService) {
        this.parentPortalService = parentPortalService;
    }

    @GetMapping("/children")
    public ResponseEntity<List<ChildResponse>> listMyChildren(@AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(parentPortalService.listMyChildren(actor.userId()));
    }

    @GetMapping("/children/{studentId}/classes/{classId}/grades")
    public ResponseEntity<List<GradeEntryResponse>> listGrades(@PathVariable Long studentId, @PathVariable Long classId,
                                                                 @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(parentPortalService.listGrades(studentId, classId, actor.userId()));
    }

    @GetMapping("/children/{studentId}/classes/{classId}/academic-terms/{academicTermId}/evaluation/{evaluationType}/result")
    public ResponseEntity<GradeEvaluationResultResponse> getEvaluationResult(@PathVariable Long studentId, @PathVariable Long classId,
                                                                      @PathVariable Long academicTermId,
                                                                      @PathVariable String evaluationType,
                                                                      @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(parentPortalService.getEvaluationResult(studentId, classId, academicTermId, evaluationType, actor.userId()));
    }

    @GetMapping("/children/{studentId}/classes/{classId}/attendance")
    public ResponseEntity<List<AttendanceMarkResponse>> listAttendance(@PathVariable Long studentId, @PathVariable Long classId,
                                                                         @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(parentPortalService.listAttendance(studentId, classId, actor.userId()));
    }

    @GetMapping("/children/{studentId}/classes/{classId}/comments")
    public ResponseEntity<List<StudentCommentResponse>> listComments(@PathVariable Long studentId, @PathVariable Long classId,
                                                                       @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(parentPortalService.listComments(studentId, classId, actor.userId()));
    }

    @GetMapping("/children/{studentId}/classes/{classId}/schedule")
    public ResponseEntity<List<ClassSessionResponse>> listSchedule(@PathVariable Long studentId, @PathVariable Long classId,
                                                                     @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(parentPortalService.listSchedule(studentId, classId, actor.userId()));
    }

    @GetMapping("/children/{studentId}/classes/{classId}/homework")
    public ResponseEntity<List<HomeworkProgressResponse>> listHomeworkProgress(
            @PathVariable Long studentId, @PathVariable Long classId,
            @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(parentPortalService.listHomeworkProgress(studentId, classId, actor.userId()));
    }
}
