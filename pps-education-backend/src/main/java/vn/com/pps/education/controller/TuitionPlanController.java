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
import vn.com.pps.education.dto.AssignTuitionPlanRequest;
import vn.com.pps.education.dto.CreateTuitionPlanRequest;
import vn.com.pps.education.dto.TuitionPlanAssignmentResponse;
import vn.com.pps.education.dto.TuitionPlanResponse;
import vn.com.pps.education.dto.UpdateTuitionPlanStatusRequest;
import vn.com.pps.education.security.AuthenticatedUser;
import vn.com.pps.education.service.TuitionPlanService;

/** Định mức học phí — hạ tầng cho UC-30, xem Javadoc TuitionPlanService. */
@RestController
public class TuitionPlanController {

    private final TuitionPlanService tuitionPlanService;

    public TuitionPlanController(TuitionPlanService tuitionPlanService) {
        this.tuitionPlanService = tuitionPlanService;
    }

    @PostMapping("/api/finance/tuition-plans")
    @PreAuthorize("hasPermission(null, 'finance.tuition-plan.manage')")
    public ResponseEntity<TuitionPlanResponse> createPlan(@Valid @RequestBody CreateTuitionPlanRequest request,
                                                             @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(tuitionPlanService.createPlan(request, actor.userId()));
    }

    @GetMapping("/api/finance/tuition-plans/{id}")
    @PreAuthorize("hasPermission(null, 'finance.tuition-plan.manage')")
    public ResponseEntity<TuitionPlanResponse> getPlan(@PathVariable Long id) {
        return ResponseEntity.ok(tuitionPlanService.getPlan(id));
    }

    @PostMapping("/api/finance/tuition-plan-assignments")
    @PreAuthorize("hasPermission(null, 'finance.tuition-plan.manage')")
    public ResponseEntity<TuitionPlanAssignmentResponse> assignToClass(@Valid @RequestBody AssignTuitionPlanRequest request,
                                                                          @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(tuitionPlanService.assignToClass(request, actor.userId()));
    }

    @PutMapping("/api/finance/tuition-plans/{id}/status")
    @PreAuthorize("hasPermission(null, 'finance.tuition-plan.manage')")
    public ResponseEntity<TuitionPlanResponse> updateStatus(@PathVariable Long id,
                                                               @Valid @RequestBody UpdateTuitionPlanStatusRequest request,
                                                               @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(tuitionPlanService.updateStatus(id, request, actor.userId()));
    }
}
