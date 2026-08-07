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
import vn.com.pps.education.dto.AcademicYearResponse;
import vn.com.pps.education.dto.CreateAcademicYearRequest;
import vn.com.pps.education.dto.UpdateAcademicYearRequest;
import vn.com.pps.education.security.AuthenticatedUser;
import vn.com.pps.education.service.AcademicYearService;

import java.util.List;

/** V103 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-07) — xem Javadoc AcademicYearService. */
@RestController
public class AcademicYearController {

    private final AcademicYearService academicYearService;

    public AcademicYearController(AcademicYearService academicYearService) {
        this.academicYearService = academicYearService;
    }

    @PreAuthorize("hasPermission(null, 'academic.class.manage')")
    @PostMapping("/api/academic-years")
    public ResponseEntity<AcademicYearResponse> create(@Valid @RequestBody CreateAcademicYearRequest request,
                                                          @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(academicYearService.create(request, actor.userId()));
    }

    @GetMapping("/api/academic-years")
    public ResponseEntity<List<AcademicYearResponse>> list() {
        return ResponseEntity.ok(academicYearService.list());
    }

    @GetMapping("/api/academic-years/{id}")
    public ResponseEntity<AcademicYearResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(academicYearService.getById(id));
    }

    @PreAuthorize("hasPermission(null, 'academic.class.manage')")
    @PutMapping("/api/academic-years/{id}")
    public ResponseEntity<AcademicYearResponse> update(@PathVariable Long id, @Valid @RequestBody UpdateAcademicYearRequest request,
                                                          @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(academicYearService.update(id, request, actor.userId()));
    }
}
