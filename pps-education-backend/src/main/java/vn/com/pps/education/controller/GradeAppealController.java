package vn.com.pps.education.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import vn.com.pps.education.dto.GradeAppealResponse;
import vn.com.pps.education.dto.SubmitGradeAppealRequest;
import vn.com.pps.education.security.AuthenticatedUser;
import vn.com.pps.education.service.GradeAppealService;

import java.util.List;

/**
 * UC-62: Phúc khảo điểm — xem Javadoc GradeAppealService. Tự-phục vụ
 * (self-service), không cần permission code riêng — quyền sở hữu (học
 * sinh/phụ huynh) và phân công giảng dạy (giáo viên) đã kiểm tra trong
 * Service, đúng pattern GradeController (enterGrade không cần
 * @PreAuthorize riêng).
 */
@RestController
public class GradeAppealController {

    private final GradeAppealService gradeAppealService;

    public GradeAppealController(GradeAppealService gradeAppealService) {
        this.gradeAppealService = gradeAppealService;
    }

    @PostMapping("/api/grade-appeals")
    public ResponseEntity<GradeAppealResponse> submitAppeal(@Valid @RequestBody SubmitGradeAppealRequest request,
                                                              @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(gradeAppealService.submitAppeal(request, actor.userId()));
    }

    @GetMapping("/api/grade-appeals/me")
    public ResponseEntity<List<GradeAppealResponse>> listMyAppeals(@AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(gradeAppealService.listMyAppeals(actor.userId()));
    }

    @GetMapping("/api/grade-appeals/pending")
    public ResponseEntity<List<GradeAppealResponse>> listPendingForMyClasses(@AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(gradeAppealService.listPendingForMyClasses(actor.userId()));
    }

    @PostMapping("/api/grade-appeals/{id}/accept")
    public ResponseEntity<GradeAppealResponse> acceptAppeal(@PathVariable Long id,
                                                              @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(gradeAppealService.acceptAppeal(id, actor.userId()));
    }
}
