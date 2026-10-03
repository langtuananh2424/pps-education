package vn.com.pps.education.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.com.pps.education.dto.CreateScholarshipRequest;
import vn.com.pps.education.dto.ScholarshipResponse;
import vn.com.pps.education.security.AuthenticatedUser;
import vn.com.pps.education.service.ScholarshipService;

import java.util.List;

/** Học bổng/Miễn giảm — hạ tầng cho UC-30 A3, xem Javadoc ScholarshipService. */
@RestController
public class ScholarshipController {

    private final ScholarshipService scholarshipService;

    public ScholarshipController(ScholarshipService scholarshipService) {
        this.scholarshipService = scholarshipService;
    }

    @PostMapping("/api/finance/scholarships")
    @PreAuthorize("hasPermission(null, 'finance.scholarship.create')")
    public ResponseEntity<ScholarshipResponse> create(@Valid @RequestBody CreateScholarshipRequest request,
                                                         @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(scholarshipService.create(request, actor.userId()));
    }

    /** Không có mã quyền "xem học bổng" riêng — ai được xem hóa đơn hoặc cấp/thu hồi học bổng đều xem được danh sách. */
    @GetMapping("/api/finance/scholarships")
    @PreAuthorize("hasPermission(null, 'finance.invoice.view') or hasPermission(null, 'finance.scholarship.create') or hasPermission(null, 'finance.scholarship.revoke')")
    public ResponseEntity<List<ScholarshipResponse>> list(@RequestParam(required = false) Long studentId,
                                                             @RequestParam(required = false) String status) {
        return ResponseEntity.ok(scholarshipService.list(studentId, status));
    }

    @PostMapping("/api/finance/scholarships/{id}/revoke")
    @PreAuthorize("hasPermission(null, 'finance.scholarship.revoke')")
    public ResponseEntity<ScholarshipResponse> revoke(@PathVariable Long id) {
        return ResponseEntity.ok(scholarshipService.revoke(id));
    }
}
