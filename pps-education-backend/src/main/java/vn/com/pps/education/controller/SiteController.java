package vn.com.pps.education.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import vn.com.pps.education.dto.AssignSiteManagerRequest;
import vn.com.pps.education.dto.AssignSiteTeacherRequest;
import vn.com.pps.education.dto.CreateSiteRequest;
import vn.com.pps.education.dto.PartnerAttendanceSummaryResponse;
import vn.com.pps.education.dto.SiteResponse;
import vn.com.pps.education.dto.SiteTeacherResponse;
import vn.com.pps.education.dto.UpdateSiteRequest;
import vn.com.pps.education.security.AuthenticatedUser;
import vn.com.pps.education.service.SiteService;
import vn.com.pps.education.service.StudentAttendanceService;

import java.util.List;

/** UC-36: Quản lý điểm trường (FR-FAC-01) — xem Javadoc SiteService. */
@RestController
public class SiteController {

    private final SiteService siteService;
    private final StudentAttendanceService studentAttendanceService;

    public SiteController(SiteService siteService, StudentAttendanceService studentAttendanceService) {
        this.siteService = siteService;
        this.studentAttendanceService = studentAttendanceService;
    }

    @PostMapping("/api/sites")
    @PreAuthorize("hasPermission(null, 'facility.manage')")
    public ResponseEntity<SiteResponse> createSite(@Valid @RequestBody CreateSiteRequest request,
                                                     @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(siteService.createSite(request, actor.userId()));
    }

    @PutMapping("/api/sites/{id}")
    @PreAuthorize("hasPermission(null, 'facility.manage')")
    public ResponseEntity<SiteResponse> updateSite(@PathVariable Long id,
                                                     @Valid @RequestBody UpdateSiteRequest request,
                                                     @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(siteService.updateSite(id, request, actor.userId()));
    }

    @PutMapping("/api/sites/{id}/manager")
    @PreAuthorize("hasPermission(null, 'facility.manage')")
    public ResponseEntity<SiteResponse> assignManager(@PathVariable Long id,
                                                        @Valid @RequestBody AssignSiteManagerRequest request,
                                                        @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(siteService.assignManager(id, request, actor.userId()));
    }

    @GetMapping("/api/sites/{id}")
    public ResponseEntity<SiteResponse> getSite(@PathVariable Long id) {
        return ResponseEntity.ok(siteService.getSite(id));
    }

    @GetMapping("/api/sites")
    public ResponseEntity<List<SiteResponse>> listSites() {
        return ResponseEntity.ok(siteService.listSites());
    }

    @PostMapping("/api/sites/{id}/teachers")
    @PreAuthorize("hasPermission(null, 'facility.manage')")
    public ResponseEntity<SiteTeacherResponse> assignTeacher(@PathVariable Long id,
                                                              @Valid @RequestBody AssignSiteTeacherRequest request,
                                                              @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(siteService.assignTeacher(id, request, actor.userId()));
    }

    @DeleteMapping("/api/sites/{id}/teachers/{siteTeacherId}")
    @PreAuthorize("hasPermission(null, 'facility.manage')")
    public ResponseEntity<Void> removeTeacher(@PathVariable Long id, @PathVariable Long siteTeacherId,
                                               @AuthenticationPrincipal AuthenticatedUser actor) {
        siteService.removeTeacherFromSite(id, siteTeacherId, actor.userId());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/sites/{id}/teachers")
    public ResponseEntity<List<SiteTeacherResponse>> listTeachers(@PathVariable Long id) {
        return ResponseEntity.ok(siteService.listTeachers(id));
    }

    /** UC-15b: Quản lý điểm trường xem báo cáo chuyên cần của điểm trường mình phụ trách. */
    @GetMapping("/api/sites/{id}/attendance-summary")
    public ResponseEntity<List<PartnerAttendanceSummaryResponse>> getAttendanceSummary(
            @PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(studentAttendanceService.getSiteSummary(id, actor.userId()));
    }
}
