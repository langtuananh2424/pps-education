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
import org.springframework.web.multipart.MultipartFile;
import vn.com.pps.education.common.ExcelHttpResponses;
import vn.com.pps.education.dto.ClassSessionLessonContentResponse;
import vn.com.pps.education.dto.CreateStudentCommentRequest;
import vn.com.pps.education.dto.DailyCommentImportResponse;
import vn.com.pps.education.dto.DecideCommentsRequest;
import vn.com.pps.education.dto.StudentCommentResponse;
import vn.com.pps.education.dto.SubmitCommentsRequest;
import vn.com.pps.education.dto.UpdateLessonContentRequest;
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

    // ---- Nhận xét Hàng ngày kiểu mới — Excel round-trip (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-07-24) ----

    /** "Bài học hôm nay" (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-07-29 — chuyển từ Điểm danh sang Nhận xét). */
    @PreAuthorize("hasPermission(null, 'academic.comment.write') or hasPermission(null, 'academic.comment.approve')")
    @PutMapping("/api/class-sessions/{classSessionId}/comments/lesson-content")
    public ResponseEntity<ClassSessionLessonContentResponse> updateLessonContent(@PathVariable Long classSessionId,
                                                                                  @Valid @RequestBody UpdateLessonContentRequest request,
                                                                                  @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(studentCommentService.updateLessonContent(classSessionId, request.lessonContent(), actor.userId()));
    }

    @PreAuthorize("hasPermission(null, 'academic.comment.write') or hasPermission(null, 'academic.comment.approve')")
    @GetMapping("/api/class-sessions/{classSessionId}/comments/template")
    public ResponseEntity<byte[]> downloadTemplate(@PathVariable Long classSessionId,
                                                    @AuthenticationPrincipal AuthenticatedUser actor) {
        byte[] content = studentCommentService.buildTemplate(classSessionId, actor.userId());
        return ExcelHttpResponses.attachment(content, "mau-nhan-xet-buoi-" + classSessionId + ".xlsx");
    }

    @PreAuthorize("hasPermission(null, 'academic.comment.write') or hasPermission(null, 'academic.comment.approve')")
    @PostMapping(value = "/api/class-sessions/{classSessionId}/comments/import", consumes = "multipart/form-data")
    public ResponseEntity<DailyCommentImportResponse> importComments(@PathVariable Long classSessionId,
                                                                       @RequestParam("file") MultipartFile file,
                                                                       @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(studentCommentService.importComments(classSessionId, file, actor.userId()));
    }
}
