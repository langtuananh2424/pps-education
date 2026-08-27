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
import vn.com.pps.education.dto.AutoProgressPreviewResponse;
import vn.com.pps.education.dto.BulkUpdateHomeworkDueDateRequest;
import vn.com.pps.education.dto.ClassSessionLessonContentResponse;
import vn.com.pps.education.dto.ClassSessionTeacherNameResponse;
import vn.com.pps.education.dto.ClassSessionTeacherTypeResponse;
import vn.com.pps.education.dto.CreateStudentCommentRequest;
import vn.com.pps.education.dto.DailyCommentImportPreviewResponse;
import vn.com.pps.education.dto.DailyCommentImportResponse;
import vn.com.pps.education.dto.DecideCommentsRequest;
import vn.com.pps.education.dto.StudentCommentHistoryResponse;
import vn.com.pps.education.dto.StudentCommentResponse;
import vn.com.pps.education.dto.SubmitCommentsRequest;
import vn.com.pps.education.dto.UpdateActualTeacherNameRequest;
import vn.com.pps.education.dto.UpdateLessonContentRequest;
import vn.com.pps.education.dto.UpdateSessionTeacherTypeRequest;
import vn.com.pps.education.dto.UpdateStudentCommentContentRequest;
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

    /** studentId bỏ trống (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-12) — trả về TOÀN BỘ nhận xét của cả lớp trong 1 lần gọi, thay N request/học sinh (xem StudentCommentService#listCommentsForClass). */
    @GetMapping("/api/classes/{classId}/comments")
    public ResponseEntity<List<StudentCommentResponse>> listComments(@PathVariable Long classId,
                                                                       @RequestParam(required = false) Long studentId) {
        return ResponseEntity.ok(studentId != null
                ? studentCommentService.listComments(classId, studentId)
                : studentCommentService.listCommentsForClass(classId));
    }

    /** Bổ sung ngoài SDD gốc (đã xác nhận với người dùng 2026-08-19) — version history kiểu Google Sheets: xem lại từng lần Lưu nháp/Gửi/Duyệt/Từ chối của 1 nhận xét, kèm nội dung đầy đủ tại mỗi thời điểm. */
    @GetMapping("/api/student-comments/{id}/history")
    public ResponseEntity<List<StudentCommentHistoryResponse>> listHistory(@PathVariable Long id) {
        return ResponseEntity.ok(studentCommentService.listHistory(id));
    }

    /** Bổ sung ngoài SDD gốc (đã xác nhận với người dùng 2026-08-19) — mirror listHistory nhưng gộp CẢ BUỔI (mọi học sinh) — dùng cho nút "Lịch sử phiên bản" ở đầu bảng Nhận xét hàng ngày. */
    @GetMapping("/api/class-sessions/{sessionId}/comments/history")
    public ResponseEntity<List<StudentCommentHistoryResponse>> listHistoryForSession(@PathVariable Long sessionId) {
        return ResponseEntity.ok(studentCommentService.listHistoryForSession(sessionId));
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

    /** Bổ sung ngoài SDD gốc (2026-08-24, xác nhận với người dùng) — đổi Hạn nộp BTVN buổi sau cho TOÀN BỘ nhận xét NHÁP/Bị từ chối của 1 buổi trong 1 lần gọi. Xem Javadoc StudentCommentService#bulkUpdatePendingDueDate. */
    @PreAuthorize("hasPermission(null, 'academic.comment.write') or hasPermission(null, 'academic.comment.approve')")
    @PutMapping("/api/class-sessions/{sessionId}/comments/due-date")
    public ResponseEntity<List<StudentCommentResponse>> bulkUpdatePendingDueDate(@PathVariable Long sessionId,
                                                                                    @Valid @RequestBody BulkUpdateHomeworkDueDateRequest request,
                                                                                    @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(studentCommentService.bulkUpdatePendingDueDate(sessionId, request.dueDate(), actor.userId()));
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

    /** Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-02 — Quản lý điểm trường sửa trực tiếp nội dung khi đang PENDING. */
    @PreAuthorize("hasPermission(null, 'academic.comment.approve')")
    @PutMapping("/api/comments/pending/{id}/content")
    public ResponseEntity<StudentCommentResponse> updatePendingCommentContent(@PathVariable Long id,
                                                                                @Valid @RequestBody UpdateStudentCommentContentRequest request,
                                                                                @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(studentCommentService.updatePendingCommentContent(id, request, actor.userId()));
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

    /** "Loại giáo viên" của buổi học (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-05). */
    @PreAuthorize("hasPermission(null, 'academic.comment.write') or hasPermission(null, 'academic.comment.approve')")
    @PutMapping("/api/class-sessions/{classSessionId}/comments/teacher-type")
    public ResponseEntity<ClassSessionTeacherTypeResponse> updateSessionTeacherType(@PathVariable Long classSessionId,
                                                                                      @Valid @RequestBody UpdateSessionTeacherTypeRequest request,
                                                                                      @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(studentCommentService.updateSessionTeacherType(classSessionId, request.teacherType(), actor.userId()));
    }

    /** "Tên giáo viên giảng dạy" thực tế của buổi học (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-06). */
    @PreAuthorize("hasPermission(null, 'academic.comment.write') or hasPermission(null, 'academic.comment.approve')")
    @PutMapping("/api/class-sessions/{classSessionId}/comments/teacher-name")
    public ResponseEntity<ClassSessionTeacherNameResponse> updateActualTeacherName(@PathVariable Long classSessionId,
                                                                                     @Valid @RequestBody UpdateActualTeacherNameRequest request,
                                                                                     @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(studentCommentService.updateActualTeacherName(classSessionId, request.actualTeacherName(), actor.userId()));
    }

    /** V146: % TỰ ĐỘNG "BTVN buổi trước" cho cả lớp, tính ngay cả khi buổi đang xem chưa có StudentComment nào. */
    @PreAuthorize("hasPermission(null, 'academic.comment.write') or hasPermission(null, 'academic.comment.approve')")
    @GetMapping("/api/class-sessions/{classSessionId}/comments/auto-progress-preview")
    public ResponseEntity<List<AutoProgressPreviewResponse>> previewAutoProgress(@PathVariable Long classSessionId,
                                                                                  @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(studentCommentService.previewAutoProgress(classSessionId, actor.userId()));
    }

    @PreAuthorize("hasPermission(null, 'academic.comment.write') or hasPermission(null, 'academic.comment.approve')")
    @GetMapping("/api/class-sessions/{classSessionId}/comments/template")
    public ResponseEntity<byte[]> downloadTemplate(@PathVariable Long classSessionId,
                                                    @AuthenticationPrincipal AuthenticatedUser actor) {
        byte[] content = studentCommentService.buildTemplate(classSessionId, actor.userId());
        int sessionNumber = studentCommentService.sessionNumberOf(classSessionId);
        return ExcelHttpResponses.attachment(content, "mau-nhan-xet-buoi-" + sessionNumber + ".xlsx");
    }

    @PreAuthorize("hasPermission(null, 'academic.comment.write') or hasPermission(null, 'academic.comment.approve')")
    @PostMapping(value = "/api/class-sessions/{classSessionId}/comments/import", consumes = "multipart/form-data")
    public ResponseEntity<DailyCommentImportResponse> importComments(@PathVariable Long classSessionId,
                                                                       @RequestParam("file") MultipartFile file,
                                                                       @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(studentCommentService.importComments(classSessionId, file, actor.userId()));
    }

    /** UC-21 (bổ sung ngoài SDD gốc, 2026-08-14): xem trước file Excel BTVN — chỉ parse & trả về, chưa ghi DB (trừ điểm danh). */
    @PreAuthorize("hasPermission(null, 'academic.comment.write') or hasPermission(null, 'academic.comment.approve')")
    @PostMapping(value = "/api/class-sessions/{classSessionId}/comments/import-preview", consumes = "multipart/form-data")
    public ResponseEntity<DailyCommentImportPreviewResponse> previewImportComments(@PathVariable Long classSessionId,
                                                                                     @RequestParam("file") MultipartFile file,
                                                                                     @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(studentCommentService.previewImportComments(classSessionId, file, actor.userId()));
    }
}
