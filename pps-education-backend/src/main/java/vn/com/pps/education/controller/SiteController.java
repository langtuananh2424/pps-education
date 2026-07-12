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
import org.springframework.web.bind.annotation.RestController;
import vn.com.pps.education.dto.AssignSiteManagerRequest;
import vn.com.pps.education.dto.CreateSiteRequest;
import vn.com.pps.education.dto.SiteResponse;
import vn.com.pps.education.dto.UpdateSiteRequest;
import vn.com.pps.education.security.AuthenticatedUser;
import vn.com.pps.education.service.SiteService;

import java.util.List;

/** UC-36: Quản lý điểm trường (FR-FAC-01) — xem Javadoc SiteService. */
@RestController
public class SiteController {

    private final SiteService siteService;

    public SiteController(SiteService siteService) {
        this.siteService = siteService;
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
}
