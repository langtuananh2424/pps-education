package vn.com.pps.education.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import vn.com.pps.education.dto.TaskCancelledRetentionResponse;
import vn.com.pps.education.dto.UpdateTaskCancelledRetentionRequest;
import vn.com.pps.education.security.AuthenticatedUser;
import vn.com.pps.education.service.TaskSettingsService;

/**
 * UC-06/07 (bổ sung): thiết lập hệ thống cho Quản lý công việc — số ngày
 * giữ task CANCELLED trước khi xóa cứng. API hẹp cho đúng 1 setting này
 * (giống AcademicSettingsController cho cửa sổ điểm).
 */
@RestController
public class TaskSettingsController {

    private final TaskSettingsService taskSettingsService;

    public TaskSettingsController(TaskSettingsService taskSettingsService) {
        this.taskSettingsService = taskSettingsService;
    }

    @GetMapping("/api/task/settings/cancelled-retention-days")
    public ResponseEntity<TaskCancelledRetentionResponse> getCancelledRetention() {
        return ResponseEntity.ok(taskSettingsService.getCancelledRetention());
    }

    @PreAuthorize("hasPermission(null, 'task.manage')")
    @PutMapping("/api/task/settings/cancelled-retention-days")
    public ResponseEntity<TaskCancelledRetentionResponse> updateCancelledRetention(
            @Valid @RequestBody UpdateTaskCancelledRetentionRequest request,
            @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(taskSettingsService.updateCancelledRetentionDays(request.days(), actor.userId()));
    }
}
