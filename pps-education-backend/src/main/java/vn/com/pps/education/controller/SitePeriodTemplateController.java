package vn.com.pps.education.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import vn.com.pps.education.dto.CreateSitePeriodTemplateRequest;
import vn.com.pps.education.dto.SitePeriodTemplateResponse;
import vn.com.pps.education.dto.UpdateSitePeriodTemplateRequest;
import vn.com.pps.education.security.AuthenticatedUser;
import vn.com.pps.education.service.SitePeriodTemplateService;

import java.util.List;

/** "Tiết học theo điểm trường" — xem Javadoc SitePeriodTemplateService. */
@RestController
@RequestMapping("/api/sites/{siteId}/period-templates")
public class SitePeriodTemplateController {

    private final SitePeriodTemplateService sitePeriodTemplateService;

    public SitePeriodTemplateController(SitePeriodTemplateService sitePeriodTemplateService) {
        this.sitePeriodTemplateService = sitePeriodTemplateService;
    }

    @GetMapping
    public ResponseEntity<List<SitePeriodTemplateResponse>> listBySite(@PathVariable Long siteId) {
        return ResponseEntity.ok(sitePeriodTemplateService.listBySite(siteId));
    }

    @PreAuthorize("hasPermission(null, 'facility.site.update')")
    @PostMapping
    public ResponseEntity<SitePeriodTemplateResponse> create(@PathVariable Long siteId,
                                                                @Valid @RequestBody CreateSitePeriodTemplateRequest request,
                                                                @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(sitePeriodTemplateService.create(siteId, request, actor.userId()));
    }

    @PreAuthorize("hasPermission(null, 'facility.site.update')")
    @PutMapping("/{id}")
    public ResponseEntity<SitePeriodTemplateResponse> update(@PathVariable Long siteId, @PathVariable Long id,
                                                                @Valid @RequestBody UpdateSitePeriodTemplateRequest request) {
        return ResponseEntity.ok(sitePeriodTemplateService.update(siteId, id, request));
    }

    @PreAuthorize("hasPermission(null, 'facility.site.update')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long siteId, @PathVariable Long id) {
        sitePeriodTemplateService.delete(siteId, id);
        return ResponseEntity.noContent().build();
    }
}
