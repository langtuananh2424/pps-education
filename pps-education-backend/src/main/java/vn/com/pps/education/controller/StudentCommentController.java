package vn.com.pps.education.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.com.pps.education.dto.CreateStudentCommentRequest;
import vn.com.pps.education.dto.DecideCommentsRequest;
import vn.com.pps.education.dto.StudentCommentResponse;
import vn.com.pps.education.dto.SubmitCommentsRequest;
import vn.com.pps.education.dto.UpdateStudentCommentRequest;
import vn.com.pps.education.security.AuthenticatedUser;
import vn.com.pps.education.service.StudentCommentService;

import java.util.List;

/** UC-21: Viết nhận xét (FR-ACA-04) + UC-22: Duyệt nhận xét (FR-LMS-09) — xem Javadoc StudentCommentService. */
@RestController
public class StudentCommentController {

    private final StudentCommentService studentCommentService;

    public StudentCommentController(StudentCommentService studentCommentService) {
        this.studentCommentService = studentCommentService;
    }

    // ---- UC-21: Viết nhận xét (TEACHER) ----

    @GetMapping("/api/classes/{classId}/comments")
    public ResponseEntity<List<StudentCommentResponse>> listComments(@PathVariable Long classId,
                                                                       @RequestParam Long studentId) {
        return ResponseEntity.ok(studentCommentService.listComments(classId, studentId));
    }

    @PreAuthorize("hasPermission(null, 'academic.comment.write')")
    @PostMapping("/api/classes/{classId}/comments")
    public ResponseEntity<StudentCommentResponse> writeComment(@PathVariable Long classId,
                                                                 @Valid @RequestBody CreateStudentCommentRequest request,
                                                                 @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(studentCommentService.writeComment(classId, request, actor.userId()));
    }

    @PreAuthorize("hasPermission(null, 'academic.comment.write')")
    @PutMapping("/api/comments/{id}")
    public ResponseEntity<StudentCommentResponse> updateComment(@PathVariable Long id,
                                                                   @Valid @RequestBody UpdateStudentCommentRequest request,
                                                                   @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(studentCommentService.updateComment(id, request, actor.userId()));
    }

    @PreAuthorize("hasPermission(null, 'academic.comment.write')")
    @PostMapping("/api/classes/{classId}/comments/submit")
    public ResponseEntity<List<StudentCommentResponse>> submitComments(@PathVariable Long classId,
                                                                         @Valid @RequestBody SubmitCommentsRequest request,
                                                                         @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(studentCommentService.submitComments(classId, request, actor.userId()));
    }

    // ---- UC-22: Duyệt nhận xét (SITE_MANAGER) ----

    @GetMapping("/api/comments/pending")
    public ResponseEntity<List<StudentCommentResponse>> listPendingForSite(@AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(studentCommentService.listPendingForSite(actor.userId()));
    }

    @PreAuthorize("hasPermission(null, 'academic.comment.approve')")
    @PostMapping("/api/comments/decision")
    public ResponseEntity<List<StudentCommentResponse>> decideComments(@Valid @RequestBody DecideCommentsRequest request,
                                                                         @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(studentCommentService.decideComments(request, actor.userId()));
    }
}
