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
import vn.com.pps.education.dto.AcademicTermResponse;
import vn.com.pps.education.dto.CreateAcademicTermRequest;
import vn.com.pps.education.dto.UpdateAcademicTermRequest;
import vn.com.pps.education.security.AuthenticatedUser;
import vn.com.pps.education.service.AcademicTermService;

import java.util.List;

/** UC-18 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-07-31) — xem Javadoc AcademicTermService. */
@RestController
public class AcademicTermController {

    private final AcademicTermService academicTermService;

    public AcademicTermController(AcademicTermService academicTermService) {
        this.academicTermService = academicTermService;
    }

    @PreAuthorize("hasPermission(null, 'academic.class.manage')")
    @PostMapping("/api/academic-terms")
    public ResponseEntity<AcademicTermResponse> create(@Valid @RequestBody CreateAcademicTermRequest request,
                                                          @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(academicTermService.create(request, actor.userId()));
    }

    @GetMapping("/api/academic-terms")
    public ResponseEntity<List<AcademicTermResponse>> listBySite(@RequestParam Long siteId) {
        return ResponseEntity.ok(academicTermService.listBySite(siteId));
    }

    @GetMapping("/api/academic-terms/{id}")
    public ResponseEntity<AcademicTermResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(academicTermService.getById(id));
    }

    @PreAuthorize("hasPermission(null, 'academic.class.manage')")
    @PutMapping("/api/academic-terms/{id}")
    public ResponseEntity<AcademicTermResponse> update(@PathVariable Long id, @Valid @RequestBody UpdateAcademicTermRequest request,
                                                          @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(academicTermService.update(id, request, actor.userId()));
    }
}
