package vn.com.pps.education.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import vn.com.pps.education.dto.AddTeachingPlanItemRequest;
import vn.com.pps.education.dto.CreateTeachingPlanRequest;
import vn.com.pps.education.dto.TeachingPlanItemResponse;
import vn.com.pps.education.dto.TeachingPlanResponse;
import vn.com.pps.education.dto.UpdateTeachingPlanItemRequest;
import vn.com.pps.education.dto.UpdateTeachingPlanRequest;
import vn.com.pps.education.security.AuthenticatedUser;
import vn.com.pps.education.service.TeachingPlanService;

import java.util.List;

/** UC-28: Điền kế hoạch giảng dạy (FR-LMS-08) — xem Javadoc TeachingPlanService. */
@RestController
public class TeachingPlanController {

    private final TeachingPlanService teachingPlanService;

    public TeachingPlanController(TeachingPlanService teachingPlanService) {
        this.teachingPlanService = teachingPlanService;
    }

    @PostMapping("/api/teaching-plans")
    public ResponseEntity<TeachingPlanResponse> createPlan(@Valid @RequestBody CreateTeachingPlanRequest request,
                                                             @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(teachingPlanService.createPlan(request, actor.userId()));
    }

    @PutMapping("/api/teaching-plans/{id}")
    public ResponseEntity<TeachingPlanResponse> updatePlan(@PathVariable Long id,
                                                             @Valid @RequestBody UpdateTeachingPlanRequest request,
                                                             @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(teachingPlanService.updatePlan(id, request, actor.userId()));
    }

    @GetMapping("/api/classes/{classId}/teaching-plans")
    public ResponseEntity<List<TeachingPlanResponse>> listByClass(@PathVariable Long classId) {
        return ResponseEntity.ok(teachingPlanService.listByClass(classId));
    }

    @GetMapping("/api/teaching-plans/{id}")
    public ResponseEntity<TeachingPlanResponse> getPlan(@PathVariable Long id) {
        return ResponseEntity.ok(teachingPlanService.getPlan(id));
    }

    @PostMapping("/api/teaching-plans/{id}/items")
    public ResponseEntity<TeachingPlanItemResponse> addItem(@PathVariable Long id,
                                                              @Valid @RequestBody AddTeachingPlanItemRequest request,
                                                              @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(teachingPlanService.addItem(id, request, actor.userId()));
    }

    @GetMapping("/api/teaching-plans/{id}/items")
    public ResponseEntity<List<TeachingPlanItemResponse>> listItems(@PathVariable Long id) {
        return ResponseEntity.ok(teachingPlanService.listItems(id));
    }

    @PutMapping("/api/teaching-plans/{id}/items/{itemId}")
    public ResponseEntity<TeachingPlanItemResponse> updateItem(@PathVariable Long id,
                                                                 @PathVariable Long itemId,
                                                                 @Valid @RequestBody UpdateTeachingPlanItemRequest request,
                                                                 @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(teachingPlanService.updateItem(id, itemId, request, actor.userId()));
    }
}
