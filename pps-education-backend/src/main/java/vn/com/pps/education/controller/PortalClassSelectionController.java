package vn.com.pps.education.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.com.pps.education.dto.PortalClassOptionResponse;
import vn.com.pps.education.security.AuthenticatedUser;
import vn.com.pps.education.service.PortalClassSelectionService;

import java.util.List;

/** UC-42: Chọn lớp đang xem (Portal Học sinh/Phụ huynh, FR-LMS-12). */
@RestController
@RequestMapping("/api/portal/students/{studentId}")
public class PortalClassSelectionController {

    private final PortalClassSelectionService portalClassSelectionService;

    public PortalClassSelectionController(PortalClassSelectionService portalClassSelectionService) {
        this.portalClassSelectionService = portalClassSelectionService;
    }

    @GetMapping("/class-options")
    public ResponseEntity<List<PortalClassOptionResponse>> listClassOptions(@PathVariable Long studentId,
                                                                               @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(portalClassSelectionService.listClassOptions(studentId, actor.userId()));
    }
}
