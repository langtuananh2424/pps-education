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
import vn.com.pps.education.dto.AddTaskAttachmentRequest;
import vn.com.pps.education.dto.AddTaskCommentRequest;
import vn.com.pps.education.dto.CancelTaskRequest;
import vn.com.pps.education.dto.CreateTaskRequest;
import vn.com.pps.education.dto.ReassignTaskRequest;
import vn.com.pps.education.dto.TaskAssignmentResponse;
import vn.com.pps.education.dto.TaskAttachmentResponse;
import vn.com.pps.education.dto.TaskCommentResponse;
import vn.com.pps.education.dto.TaskResponse;
import vn.com.pps.education.dto.UpdateAssignmentStatusRequest;
import vn.com.pps.education.security.AuthenticatedUser;
import vn.com.pps.education.service.TaskService;

import java.util.List;

/** UC-06: Giao việc (FR-TSK-01) + UC-07: Cập nhật tiến độ công việc (FR-TSK-02) — xem Javadoc TaskService. */
@RestController
public class TaskController {

    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    @PostMapping("/api/tasks")
    @PreAuthorize("hasPermission(null, 'task.assign')")
    public ResponseEntity<TaskResponse> createTask(@Valid @RequestBody CreateTaskRequest request,
                                                     @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(taskService.createTask(request, actor.userId()));
    }

    /** UC-06/07 (bổ sung): hủy công việc (CANCELLED thay vì xóa) rồi giao việc mới — người giao hoặc task.manage. */
    @PostMapping("/api/tasks/{id}/cancel")
    @PreAuthorize("hasPermission(null, 'task.assign')")
    public ResponseEntity<TaskResponse> cancelTask(@PathVariable Long id,
                                                    @RequestBody(required = false) CancelTaskRequest request,
                                                    @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(taskService.cancelTask(id, request, actor.userId()));
    }

    @GetMapping("/api/tasks/{id}")
    public ResponseEntity<TaskResponse> getTask(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(taskService.getTask(id, actor.userId()));
    }

    @GetMapping("/api/tasks/created-by-me")
    public ResponseEntity<List<TaskResponse>> listTasksCreatedByMe(@AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(taskService.listTasksCreatedByMe(actor.userId()));
    }

    /**
     * UC-06/07 (bổ sung): tổng quan công việc 2 tầng — task.overview.company → toàn công ty;
     * trưởng phòng → việc của phòng mình; không có gì → 403 (FE fallback my-assignments).
     * Phân quyền nằm trong Service (không @PreAuthorize) vì tầng "trưởng phòng" theo dữ liệu head_user_id.
     */
    @GetMapping("/api/tasks/overview")
    public ResponseEntity<List<TaskResponse>> listOverview(@AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(taskService.listOverview(actor.userId()));
    }

    @GetMapping("/api/tasks/my-assignments")
    @PreAuthorize("hasPermission(null, 'task.receive')")
    public ResponseEntity<List<TaskAssignmentResponse>> listMyAssignments(@AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(taskService.listMyAssignments(actor.userId()));
    }

    @GetMapping("/api/tasks/{id}/assignments")
    public ResponseEntity<List<TaskAssignmentResponse>> listAssignments(@PathVariable Long id,
                                                                          @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(taskService.listAssignments(id, actor.userId()));
    }

    @PutMapping("/api/task-assignments/{id}/status")
    @PreAuthorize("hasPermission(null, 'task.receive') or hasPermission(null, 'task.assign')")
    public ResponseEntity<TaskAssignmentResponse> updateAssignmentStatus(@PathVariable Long id,
                                                                          @Valid @RequestBody UpdateAssignmentStatusRequest request,
                                                                          @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(taskService.updateAssignmentStatus(id, request, actor.userId()));
    }

    /** UC-07 A3: người giao việc giao lại phân công đã bị từ chối cho nhân sự khác. */
    @PostMapping("/api/tasks/{id}/reassign")
    @PreAuthorize("hasPermission(null, 'task.assign')")
    public ResponseEntity<TaskAssignmentResponse> reassign(@PathVariable Long id,
                                                            @Valid @RequestBody ReassignTaskRequest request,
                                                            @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(taskService.reassign(id, request, actor.userId()));
    }

    @PostMapping("/api/tasks/{id}/attachments")
    public ResponseEntity<TaskAttachmentResponse> addAttachment(@PathVariable Long id,
                                                                  @Valid @RequestBody AddTaskAttachmentRequest request,
                                                                  @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(taskService.addAttachment(id, request, actor.userId()));
    }

    @GetMapping("/api/tasks/{id}/attachments")
    public ResponseEntity<List<TaskAttachmentResponse>> listAttachments(@PathVariable Long id,
                                                                          @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(taskService.listAttachments(id, actor.userId()));
    }

    @PostMapping("/api/tasks/{id}/comments")
    public ResponseEntity<TaskCommentResponse> addComment(@PathVariable Long id,
                                                            @Valid @RequestBody AddTaskCommentRequest request,
                                                            @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(taskService.addComment(id, request, actor.userId()));
    }

    @GetMapping("/api/tasks/{id}/comments")
    public ResponseEntity<List<TaskCommentResponse>> listComments(@PathVariable Long id,
                                                                    @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(taskService.listComments(id, actor.userId()));
    }
}
