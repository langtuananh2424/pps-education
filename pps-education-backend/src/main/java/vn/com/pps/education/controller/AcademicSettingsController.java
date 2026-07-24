package vn.com.pps.education.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import vn.com.pps.education.dto.CommentEditWindowResponse;
import vn.com.pps.education.dto.GradeAppealWindowResponse;
import vn.com.pps.education.dto.GradeEditWindowResponse;
import vn.com.pps.education.dto.UpdateCommentEditWindowRequest;
import vn.com.pps.education.dto.UpdateGradeAppealWindowRequest;
import vn.com.pps.education.dto.UpdateGradeEditWindowRequest;
import vn.com.pps.education.security.AuthenticatedUser;
import vn.com.pps.education.service.AcademicSettingsService;

/**
 * UC-19/20/62 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng): cấu
 * hình số ngày X (độ trễ tự động công bố điểm dự kiến) và số ngày Y
 * (hạn phúc khảo). API hẹp cho đúng 2 setting này — không xây
 * SystemSettingsController tổng quát (ngoài phạm vi).
 */
@RestController
public class AcademicSettingsController {

    private final AcademicSettingsService academicSettingsService;

    public AcademicSettingsController(AcademicSettingsService academicSettingsService) {
        this.academicSettingsService = academicSettingsService;
    }

    @GetMapping("/api/academic/settings/grade-edit-window-days")
    public ResponseEntity<GradeEditWindowResponse> getGradeEditWindow() {
        return ResponseEntity.ok(academicSettingsService.getGradeEditWindow());
    }

    @PreAuthorize("hasPermission(null, 'academic.grade.manage')")
    @PutMapping("/api/academic/settings/grade-edit-window-days")
    public ResponseEntity<GradeEditWindowResponse> updateGradeEditWindow(@Valid @RequestBody UpdateGradeEditWindowRequest request,
                                                                          @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(academicSettingsService.updateGradeEditWindowDays(request.days(), actor.userId()));
    }

    @GetMapping("/api/academic/settings/grade-appeal-window-days")
    public ResponseEntity<GradeAppealWindowResponse> getGradeAppealWindow() {
        return ResponseEntity.ok(academicSettingsService.getGradeAppealWindow());
    }

    @PreAuthorize("hasPermission(null, 'academic.grade.manage')")
    @PutMapping("/api/academic/settings/grade-appeal-window-days")
    public ResponseEntity<GradeAppealWindowResponse> updateGradeAppealWindow(@Valid @RequestBody UpdateGradeAppealWindowRequest request,
                                                                              @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(academicSettingsService.updateGradeAppealWindowDays(request.days(), actor.userId()));
    }

    @GetMapping("/api/academic/settings/comment-edit-window-days")
    public ResponseEntity<CommentEditWindowResponse> getCommentEditWindow() {
        return ResponseEntity.ok(academicSettingsService.getCommentEditWindow());
    }

    @PreAuthorize("hasPermission(null, 'academic.comment.approve')")
    @PutMapping("/api/academic/settings/comment-edit-window-days")
    public ResponseEntity<CommentEditWindowResponse> updateCommentEditWindow(@Valid @RequestBody UpdateCommentEditWindowRequest request,
                                                                              @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(academicSettingsService.updateCommentEditWindowDays(request.days(), actor.userId()));
    }
}
