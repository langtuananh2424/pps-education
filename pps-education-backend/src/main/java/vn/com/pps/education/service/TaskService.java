package vn.com.pps.education.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.domain.Department;
import vn.com.pps.education.domain.Employee;
import vn.com.pps.education.domain.Notification;
import vn.com.pps.education.domain.Task;
import vn.com.pps.education.domain.TaskAssignment;
import vn.com.pps.education.domain.TaskAssignmentHistory;
import vn.com.pps.education.domain.TaskAttachment;
import vn.com.pps.education.domain.TaskComment;
import vn.com.pps.education.domain.TaskHistory;
import vn.com.pps.education.domain.User;
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
import vn.com.pps.education.exception.AssigneeOutsideDepartmentException;
import vn.com.pps.education.exception.InvalidTaskStatusTransitionException;
import vn.com.pps.education.exception.NotTaskCreatorException;
import vn.com.pps.education.exception.NotAuthorizedForTaskOverviewException;
import vn.com.pps.education.exception.NotTaskParticipantException;
import vn.com.pps.education.exception.ResourceNotFoundException;
import vn.com.pps.education.exception.TaskAssigneeAlreadyAssignedException;
import vn.com.pps.education.repository.TaskAssignmentHistoryRepository;
import vn.com.pps.education.repository.TaskAssignmentRepository;
import vn.com.pps.education.repository.TaskAttachmentRepository;
import vn.com.pps.education.repository.TaskCommentRepository;
import vn.com.pps.education.repository.TaskHistoryRepository;
import vn.com.pps.education.repository.DepartmentRepository;
import vn.com.pps.education.repository.EmployeeRepository;
import vn.com.pps.education.repository.TaskRepository;
import vn.com.pps.education.repository.UserRepository;
import vn.com.pps.education.repository.UserRoleRepository;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * UC-06: Giao việc (FR-TSK-01) + UC-07: Cập nhật tiến độ công việc
 * (FR-TSK-02). Xem docs/uc/phan-he-03-cong-viec.md.
 *
 * Gộp CRUD task + assignment + comment + attachment vào 1 Service vì
 * cùng 1 aggregate root (Task) phục vụ đúng 2 UC liên tiếp trong cùng 1
 * vòng đời — giống pattern EmployeeService (Employee+Contract+Qualification).
 *
 * assignment_status thêm PENDING_REVIEW ngoài SDD gốc để khớp Kanban 4
 * cột "Cần làm→Đang làm→Chờ duyệt→Hoàn thành" (UC-07/FR-TSK-02) — đã xác
 * nhận với user, xem Javadoc migration V23.
 */
@Service
public class TaskService {

    private static final Map<TaskAssignment.Status, Set<TaskAssignment.Status>> ASSIGNEE_TRANSITIONS = Map.of(
            TaskAssignment.Status.PENDING, Set.of(TaskAssignment.Status.ACCEPTED, TaskAssignment.Status.DECLINED, TaskAssignment.Status.IN_PROGRESS),
            TaskAssignment.Status.ACCEPTED, Set.of(TaskAssignment.Status.IN_PROGRESS),
            TaskAssignment.Status.IN_PROGRESS, Set.of(TaskAssignment.Status.PENDING_REVIEW)
    );
    private static final Map<TaskAssignment.Status, Set<TaskAssignment.Status>> ASSIGNER_TRANSITIONS = Map.of(
            TaskAssignment.Status.PENDING_REVIEW, Set.of(TaskAssignment.Status.COMPLETED, TaskAssignment.Status.IN_PROGRESS)
    );

    private final TaskRepository taskRepository;
    private final TaskAssignmentRepository taskAssignmentRepository;
    private final TaskAttachmentRepository taskAttachmentRepository;
    private final TaskCommentRepository taskCommentRepository;
    private final TaskHistoryRepository taskHistoryRepository;
    private final TaskAssignmentHistoryRepository taskAssignmentHistoryRepository;
    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final EmployeeRepository employeeRepository;
    private final DepartmentRepository departmentRepository;
    private final PermissionEvaluationService permissionEvaluationService;
    private final NotificationService notificationService;

    public TaskService(TaskRepository taskRepository,
                        TaskAssignmentRepository taskAssignmentRepository,
                        TaskAttachmentRepository taskAttachmentRepository,
                        TaskCommentRepository taskCommentRepository,
                        TaskHistoryRepository taskHistoryRepository,
                        TaskAssignmentHistoryRepository taskAssignmentHistoryRepository,
                        UserRepository userRepository,
                        UserRoleRepository userRoleRepository,
                        EmployeeRepository employeeRepository,
                        DepartmentRepository departmentRepository,
                        PermissionEvaluationService permissionEvaluationService,
                        NotificationService notificationService) {
        this.taskRepository = taskRepository;
        this.taskAssignmentRepository = taskAssignmentRepository;
        this.taskAttachmentRepository = taskAttachmentRepository;
        this.taskCommentRepository = taskCommentRepository;
        this.taskHistoryRepository = taskHistoryRepository;
        this.taskAssignmentHistoryRepository = taskAssignmentHistoryRepository;
        this.userRepository = userRepository;
        this.userRoleRepository = userRoleRepository;
        this.employeeRepository = employeeRepository;
        this.departmentRepository = departmentRepository;
        this.permissionEvaluationService = permissionEvaluationService;
        this.notificationService = notificationService;
    }

    /** Main Flow bước 1-6, A2 (giao hàng loạt): tạo task + 1 task_assignment/người nhận, thông báo từng người. */
    @Transactional
    public TaskResponse createTask(CreateTaskRequest request, Long actorUserId) {
        User actor = getUserOrThrow(actorUserId);
        Employee actorEmployee = employeeRepository.findByUserId(actorUserId)
                .orElseThrow(() -> new ResourceNotFoundException("error.task.employeeProfileMissing", new Object[]{}, "Tài khoản chưa có hồ sơ nhân sự."));
        List<User> assignees = userRepository.findAllById(request.assigneeUserIds());
        if (assignees.size() != request.assigneeUserIds().size()) {
            throw new ResourceNotFoundException("error.task.assigneeNotFound", new Object[]{}, "Có người nhận việc không tồn tại trong danh sách assigneeUserIds.");
        }
        Map<Long, Employee> assigneeEmployeesByUserId = employeeRepository.findByUserIdIn(request.assigneeUserIds()).stream()
                .collect(Collectors.toMap(e -> e.getUser().getId(), e -> e));
        requireAssignScope(actorUserId, assignees, assigneeEmployeesByUserId);

        Task task = new Task();
        task.setTaskCode(generateTaskCode());
        task.setTitle(request.title());
        task.setDescription(request.description());
        task.setCreatedBy(actor);
        task.setDepartment(actorEmployee.getDepartment());
        if (request.taskType() != null) {
            task.setTaskType(Task.TaskType.valueOf(request.taskType()));
        }
        if (request.priority() != null) {
            task.setPriority(Task.Priority.valueOf(request.priority()));
        }
        task.setDueAt(request.dueAt());
        task.setTags(request.tags());
        task = taskRepository.save(task);
        writeTaskHistory(task, actor, TaskHistory.Action.CREATED);

        for (User assignee : assignees) {
            TaskAssignment assignment = new TaskAssignment();
            assignment.setTask(task);
            assignment.setAssignee(assignee);
            assignment = taskAssignmentRepository.save(assignment);
            writeAssignmentHistory(assignment, actor, TaskAssignmentHistory.Action.CREATED);

            String content = "Công việc \"%s\" đã được giao cho bạn%s.".formatted(task.getTitle(),
                    task.getDueAt() == null ? "" : ", hạn: " + task.getDueAt());
            notificationService.notify(assignee.getId(), Notification.NotificationType.TASK_ASSIGNED,
                    "Bạn được giao việc mới", content);
        }

        return toResponse(task);
    }

    /** Main Flow bước 2: đính kèm tệp tin (coi như đã upload CDN, chỉ nhận URL — giống pattern LessonMaterial). */
    @Transactional
    public TaskAttachmentResponse addAttachment(Long taskId, AddTaskAttachmentRequest request, Long actorUserId) {
        Task task = getTaskOrThrow(taskId);
        requireParticipant(task, actorUserId);
        User actor = getUserOrThrow(actorUserId);

        TaskAttachment attachment = new TaskAttachment();
        attachment.setTask(task);
        attachment.setFileUrl(request.fileUrl());
        attachment.setFileName(request.fileName());
        attachment.setUploadedBy(actor);
        attachment = taskAttachmentRepository.save(attachment);
        return toResponse(attachment);
    }

    @Transactional(readOnly = true)
    public List<TaskAttachmentResponse> listAttachments(Long taskId, Long actorUserId) {
        Task task = getTaskOrThrow(taskId);
        requireParticipant(task, actorUserId);
        return taskAttachmentRepository.findByTaskId(taskId).stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public TaskResponse getTask(Long taskId, Long actorUserId) {
        Task task = getTaskOrThrow(taskId);
        requireParticipant(task, actorUserId);
        return toResponse(task);
    }

    /** Main Flow bước 5: cấp quản lý theo dõi tổng quan công việc đã giao. */
    @Transactional(readOnly = true)
    public List<TaskResponse> listTasksCreatedByMe(Long actorUserId) {
        return taskRepository.findByCreatedByIdOrderByIdDesc(actorUserId).stream().map(this::toResponse).toList();
    }

    /**
     * UC-06/07 (bổ sung, xác nhận với người dùng 2026-07-22): tổng quan công
     * việc 2 tầng cho FE (GET /api/tasks/overview):
     * - Có quyền task.overview.company → toàn bộ công việc công ty.
     * - Là trưởng phòng (departments.head_user_id) → mọi công việc thuộc
     *   phòng mình làm trưởng (theo tasks.department_id = phòng người tạo,
     *   KHÔNG lọc theo người tạo).
     * - Không có cả 2 → 403 (FE fallback GET /api/tasks/my-assignments cho
     *   nhân viên thường — chỉ xem việc của mình).
     */
    @Transactional(readOnly = true)
    public List<TaskResponse> listOverview(Long actorUserId) {
        if (permissionEvaluationService.hasPermission(actorUserId, "task.overview.company")) {
            return taskRepository.findAllOrderByIdDesc().stream().map(this::toResponse).toList();
        }
        Set<Long> headedDeptIds = departmentRepository.findByHeadUserId(actorUserId).stream()
                .map(Department::getId).collect(Collectors.toSet());
        if (headedDeptIds.isEmpty()) {
            throw new NotAuthorizedForTaskOverviewException("error.notAuthorizedForTaskOverview.default", new Object[]{},
                    "Bạn không có quyền xem tổng quan công việc và không làm trưởng phòng nào.");
        }
        return taskRepository.findByDepartmentIdInOrderByIdDesc(headedDeptIds).stream().map(this::toResponse).toList();
    }

    /** UC-07 Main Flow bước 1: không gian làm việc Kanban của người nhận việc. */
    @Transactional(readOnly = true)
    public List<TaskAssignmentResponse> listMyAssignments(Long actorUserId) {
        return taskAssignmentRepository.findByAssigneeIdOrderByIdDesc(actorUserId).stream().map(this::toResponse).toList();
    }

    /**
     * UC-07 A2: người giao việc xem toàn bộ assignment (từng người nhận + trạng thái + assignmentId)
     * của 1 task để duyệt/từ chối kết quả qua updateAssignmentStatus. Chỉ người giao (createdBy) —
     * hẹp hơn requireParticipant có chủ đích: 1 người nhận không có nhu cầu nghiệp vụ nào (UC-06/07)
     * để xem tiến độ/lý do từ chối của những người nhận khác cùng task.
     */
    @Transactional(readOnly = true)
    public List<TaskAssignmentResponse> listAssignments(Long taskId, Long actorUserId) {
        Task task = getTaskOrThrow(taskId);
        requireCreator(task, actorUserId);
        return taskAssignmentRepository.findByTaskId(taskId).stream().map(this::toResponse).toList();
    }

    /**
     * UC-07 Main Flow bước 2-4, A2: chuyển trạng thái 1 task_assignment.
     * Người nhận việc (assignee) điều khiển PENDING→ACCEPTED/DECLINED/IN_PROGRESS,
     * ACCEPTED→IN_PROGRESS, IN_PROGRESS→PENDING_REVIEW ("nộp kết quả").
     * Người giao việc (assigner) điều khiển PENDING_REVIEW→COMPLETED (duyệt)
     * hoặc PENDING_REVIEW→IN_PROGRESS (A2 — từ chối, kèm lý do bắt buộc).
     */
    @Transactional
    public TaskAssignmentResponse updateAssignmentStatus(Long assignmentId, UpdateAssignmentStatusRequest request, Long actorUserId) {
        TaskAssignment assignment = taskAssignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new ResourceNotFoundException("error.task.assignmentNotFound", new Object[]{assignmentId}, "Không tìm thấy phân công công việc id=" + assignmentId));
        Task task = assignment.getTask();
        if (task.getStatus() == Task.Status.CANCELLED) {
            throw new InvalidTaskStatusTransitionException("error.invalidTaskStatusTransition.taskCancelled", new Object[]{},
                    "Công việc này đã bị hủy (CANCELLED) — không thể cập nhật phân công.");
        }
        User actor = getUserOrThrow(actorUserId);
        TaskAssignment.Status current = assignment.getStatus();
        TaskAssignment.Status target = TaskAssignment.Status.valueOf(request.status());

        boolean isAssignee = assignment.getAssignee().getId().equals(actorUserId);
        boolean isAssigner = task.getCreatedBy().getId().equals(actorUserId);
        if (!isAssignee && !isAssigner) {
            throw new NotTaskParticipantException("error.notTaskParticipant.default", new Object[]{},
                    "Bạn không phải người giao hoặc người nhận công việc này.");
        }
        boolean validAsAssignee = isAssignee && ASSIGNEE_TRANSITIONS.getOrDefault(current, Set.of()).contains(target);
        boolean isAssignerReject = current == TaskAssignment.Status.PENDING_REVIEW && target == TaskAssignment.Status.IN_PROGRESS;
        boolean validAsAssigner = isAssigner && ASSIGNER_TRANSITIONS.getOrDefault(current, Set.of()).contains(target);
        if (!validAsAssignee && !validAsAssigner) {
            throw new InvalidTaskStatusTransitionException("error.invalidTaskStatusTransition.assignment",
                    new Object[]{current, target},
                    "Không thể chuyển phân công này từ " + current + " sang " + target + ".");
        }
        boolean commentRequired = target == TaskAssignment.Status.DECLINED || (validAsAssigner && isAssignerReject);
        if (commentRequired && (request.comment() == null || request.comment().isBlank())) {
            throw new IllegalArgumentException(
                    target == TaskAssignment.Status.DECLINED
                            ? "Cần nêu lý do khi từ chối nhận việc."
                            : "Cần nêu lý do khi từ chối kết quả (A2).");
        }

        OffsetDateTime now = OffsetDateTime.now();
        if (target == TaskAssignment.Status.DECLINED) {
            assignment.setDeclineReason(request.comment());
        }
        if (target == TaskAssignment.Status.IN_PROGRESS && assignment.getStartedAt() == null) {
            assignment.setStartedAt(now);
        }
        if (target == TaskAssignment.Status.COMPLETED) {
            assignment.setCompletedAt(now);
        }
        assignment.setStatus(target);
        assignment = taskAssignmentRepository.save(assignment);
        writeAssignmentHistory(assignment, actor, TaskAssignmentHistory.Action.UPDATED);

        boolean commented = request.comment() != null && !request.comment().isBlank();
        if (commented) {
            TaskComment comment = new TaskComment();
            comment.setTask(task);
            comment.setCommenter(actor);
            comment.setContent(request.comment());
            taskCommentRepository.save(comment);
        }

        notifyAssignerIfNotable(task, assignment, actor, target, commented);
        if (validAsAssigner && isAssignerReject) {
            notifyAssigneeOnReject(task, assignment, actor);
        }

        recomputeTaskStatus(task);
        return toResponse(assignment);
    }

    /**
     * UC-07 A3: người giao việc giao lại 1 phân công đã bị từ chối (DECLINED)
     * cho nhân sự khác. Bản ghi DECLINED được GIỮ LẠI làm lịch sử; tạo 1
     * task_assignment MỚI trạng thái PENDING cho người nhận mới và thông báo
     * cho họ (FR-TSK-03). Chỉ người giao (createdBy) mới giao lại được; người
     * nhận mới phải trong phạm vi phòng ban (như UC-06 Main Flow bước 3).
     * Không giao lại được khi task đã COMPLETED/CANCELLED (không mở lại việc
     * đã đóng).
     */
    @Transactional
    public TaskAssignmentResponse reassign(Long taskId, ReassignTaskRequest request, Long actorUserId) {
        Task task = getTaskOrThrow(taskId);
        requireCreator(task, actorUserId);
        User actor = getUserOrThrow(actorUserId);

        if (task.getStatus() == Task.Status.COMPLETED || task.getStatus() == Task.Status.CANCELLED) {
            throw new InvalidTaskStatusTransitionException("error.invalidTaskStatusTransition.reassignBlocked",
                    new Object[]{task.getStatus()},
                    "Không thể giao lại công việc này đang ở trạng thái " + task.getStatus() + ".");
        }

        TaskAssignment declined = taskAssignmentRepository.findById(request.fromAssignmentId())
                .orElseThrow(() -> new ResourceNotFoundException("error.task.assignmentNotFound", new Object[]{request.fromAssignmentId()},
                        "Không tìm thấy phân công công việc id=" + request.fromAssignmentId()));
        if (!declined.getTask().getId().equals(taskId)) {
            throw new IllegalArgumentException(
                    "Phân công bạn chọn không thuộc công việc này.");
        }
        if (declined.getStatus() != TaskAssignment.Status.DECLINED) {
            throw new InvalidTaskStatusTransitionException("error.invalidTaskStatusTransition.notDeclined",
                    new Object[]{request.fromAssignmentId(), declined.getStatus()},
                    "Chỉ giao lại được phân công đã bị từ chối (DECLINED); phân công id="
                            + request.fromAssignmentId() + " hiện là " + declined.getStatus() + ".");
        }

        User newAssignee = getUserOrThrow(request.newAssigneeUserId());
        Map<Long, Employee> newAssigneeEmployeeByUserId = employeeRepository.findByUserIdIn(List.of(newAssignee.getId())).stream()
                .collect(Collectors.toMap(e -> e.getUser().getId(), e -> e));
        requireAssignScope(actorUserId, List.of(newAssignee), newAssigneeEmployeeByUserId);

        if (taskAssignmentRepository.findByTaskIdAndAssigneeId(taskId, newAssignee.getId()).isPresent()) {
            throw new TaskAssigneeAlreadyAssignedException("error.taskAssigneeAlreadyAssigned.default", new Object[]{},
                    "Người nhận mới đã có phân công trong công việc này rồi.");
        }

        TaskAssignment fresh = new TaskAssignment();
        fresh.setTask(task);
        fresh.setAssignee(newAssignee);
        fresh = taskAssignmentRepository.save(fresh);
        writeAssignmentHistory(fresh, actor, TaskAssignmentHistory.Action.CREATED);

        if (request.comment() != null && !request.comment().isBlank()) {
            TaskComment comment = new TaskComment();
            comment.setTask(task);
            comment.setCommenter(actor);
            comment.setContent(request.comment());
            taskCommentRepository.save(comment);
        }

        String content = "Công việc \"%s\" đã được giao lại cho bạn%s.".formatted(task.getTitle(),
                task.getDueAt() == null ? "" : ", hạn: " + task.getDueAt());
        notificationService.notify(newAssignee.getId(), Notification.NotificationType.TASK_ASSIGNED,
                "Bạn được giao lại việc", content);

        return toResponse(fresh);
    }

    /**
     * UC-06/07 (bổ sung, xác nhận với người dùng 2026-07-22): "xóa" công việc
     * đã giao KHÔNG xóa trực tiếp mà chuyển task sang CANCELLED (giữ lịch sử)
     * — sau đó người giao tự tạo việc mới nếu cần. Chỉ người giao (createdBy)
     * hoặc tài khoản có task.manage. Không hủy task đã COMPLETED/CANCELLED.
     * Cron nightly xóa cứng task CANCELLED sau task.cancelled_retention_days
     * ngày (TaskSchedulerService).
     */
    @Transactional
    public TaskResponse cancelTask(Long taskId, CancelTaskRequest request, Long actorUserId) {
        Task task = getTaskOrThrow(taskId);
        User actor = getUserOrThrow(actorUserId);
        boolean isCreator = task.getCreatedBy().getId().equals(actorUserId);
        boolean isManager = permissionEvaluationService.hasPermission(actorUserId, "task.manage");
        if (!isCreator && !isManager) {
            throw new NotTaskCreatorException("error.notTaskCreator.noManagePermission", new Object[]{},
                    "Bạn không phải người giao công việc này và không có quyền quản trị công việc.");
        }
        if (task.getStatus() == Task.Status.COMPLETED || task.getStatus() == Task.Status.CANCELLED) {
            throw new InvalidTaskStatusTransitionException("error.invalidTaskStatusTransition.cancelBlocked",
                    new Object[]{task.getStatus()},
                    "Không thể hủy công việc này đang ở trạng thái " + task.getStatus() + ".");
        }
        task.setStatus(Task.Status.CANCELLED);
        task.setCancelledAt(OffsetDateTime.now());
        task = taskRepository.save(task);
        writeTaskHistory(task, actor, TaskHistory.Action.UPDATED);

        String reason = request == null ? null : request.reason();
        if (reason != null && !reason.isBlank()) {
            TaskComment comment = new TaskComment();
            comment.setTask(task);
            comment.setCommenter(actor);
            comment.setContent("[Hủy công việc] " + reason);
            taskCommentRepository.save(comment);
        }

        // Thông báo cho các người nhận đang mở (chưa COMPLETED/DECLINED) rằng việc đã bị hủy.
        String notifyContent = "\"%s\" đã bị hủy%s.".formatted(task.getTitle(),
                reason != null && !reason.isBlank() ? ": " + reason : "");
        taskAssignmentRepository.findByTaskId(taskId).stream()
                .filter(a -> a.getStatus() != TaskAssignment.Status.COMPLETED
                        && a.getStatus() != TaskAssignment.Status.DECLINED)
                .filter(a -> !a.getAssignee().getId().equals(actorUserId))
                .forEach(a -> notificationService.notify(a.getAssignee().getId(),
                        Notification.NotificationType.TASK_ASSIGNED, "Công việc đã bị hủy", notifyContent));
        return toResponse(task);
    }

    /** UC-07 Main Flow bước 3: bình luận/phản hồi tiến độ (không nhất thiết đi kèm đổi trạng thái). */
    @Transactional
    public TaskCommentResponse addComment(Long taskId, AddTaskCommentRequest request, Long actorUserId) {
        Task task = getTaskOrThrow(taskId);
        requireParticipant(task, actorUserId);
        User actor = getUserOrThrow(actorUserId);

        TaskComment comment = new TaskComment();
        comment.setTask(task);
        comment.setCommenter(actor);
        comment.setContent(request.content());
        comment.setAttachmentUrl(request.attachmentUrl());
        comment = taskCommentRepository.save(comment);

        if (task.getCreatedBy().getId().equals(actorUserId)) {
            taskAssignmentRepository.findByTaskId(taskId).stream()
                    .filter(a -> !a.getAssignee().getId().equals(actorUserId))
                    .forEach(a -> notificationService.notify(a.getAssignee().getId(), Notification.NotificationType.TASK_COMMENT,
                            "Có phản hồi mới", "\"%s\" có phản hồi mới từ người giao việc.".formatted(task.getTitle())));
        } else {
            notificationService.notify(task.getCreatedBy().getId(), Notification.NotificationType.TASK_COMMENT,
                    "Có phản hồi mới", "\"%s\" có phản hồi mới từ %s.".formatted(task.getTitle(), actor.getFullName()));
        }
        return toResponse(comment);
    }

    @Transactional(readOnly = true)
    public List<TaskCommentResponse> listComments(Long taskId, Long actorUserId) {
        Task task = getTaskOrThrow(taskId);
        requireParticipant(task, actorUserId);
        return taskCommentRepository.findByTaskIdOrderByCreatedAtAsc(taskId).stream().map(this::toResponse).toList();
    }

    // ===================== Helpers =====================

    /**
     * Main Flow bước 3, A1 (mô hình phạm vi đã chốt với người dùng 2026-07-22):
     * - "Company-wide" (giao cho BẤT KỲ AI, kể cả trưởng phòng) = có quyền
     *   task.manage HOẶC role EXECUTIVE (Ban giám đốc).
     * - Ngược lại: chỉ TRƯỞNG PHÒNG (departments.head_user_id = actor) mới
     *   giao được, và chỉ cho nhân sự thuộc phòng do mình làm trưởng.
     * - Nhân sự thường (không company-wide, không làm trưởng phòng nào) →
     *   không có phòng đích hợp lệ → bị chặn.
     */
    private void requireAssignScope(Long actorUserId, List<User> assignees,
                                     Map<Long, Employee> assigneeEmployeesByUserId) {
        if (canAssignCompanyWide(actorUserId)) {
            return;
        }
        Set<Long> headedDeptIds = departmentRepository.findByHeadUserId(actorUserId).stream()
                .map(Department::getId).collect(Collectors.toSet());
        for (User assignee : assignees) {
            Employee assigneeEmployee = assigneeEmployeesByUserId.get(assignee.getId());
            Long assigneeDeptId = assigneeEmployee == null || assigneeEmployee.getDepartment() == null
                    ? null : assigneeEmployee.getDepartment().getId();
            if (assigneeDeptId == null || !headedDeptIds.contains(assigneeDeptId)) {
                throw new AssigneeOutsideDepartmentException("error.assigneeOutsideDepartment.default", new Object[]{},
                        "Người nhận việc không thuộc phòng ban do bạn làm trưởng phòng.");
            }
        }
    }

    /** Company-wide (giao cho bất kỳ ai): quyền task.manage HOẶC role EXECUTIVE (Ban giám đốc). */
    private boolean canAssignCompanyWide(Long actorUserId) {
        return permissionEvaluationService.hasPermission(actorUserId, "task.manage")
                || roleCodesOf(actorUserId).contains("EXECUTIVE");
    }

    private void requireCreator(Task task, Long actorUserId) {
        if (task.getCreatedBy().getId().equals(actorUserId)) {
            return;
        }
        throw new NotTaskCreatorException("error.notTaskCreator.default", new Object[]{},
                "Bạn không phải người giao công việc này.");
    }

    private void requireParticipant(Task task, Long actorUserId) {
        if (task.getCreatedBy().getId().equals(actorUserId)) {
            return;
        }
        if (taskAssignmentRepository.findByTaskIdAndAssigneeId(task.getId(), actorUserId).isPresent()) {
            return;
        }
        throw new NotTaskParticipantException("error.notTaskParticipant.default", new Object[]{},
                "Bạn không phải người giao hoặc người nhận công việc này.");
    }

    /** Main Flow bước 4: thông báo người giao việc khi có phản hồi mới HOẶC khi chuyển sang Chờ duyệt/Hoàn thành. */
    private void notifyAssignerIfNotable(Task task, TaskAssignment assignment, User actor, TaskAssignment.Status target, boolean commented) {
        boolean statusNotable = target == TaskAssignment.Status.PENDING_REVIEW || target == TaskAssignment.Status.COMPLETED;
        if (!(commented || statusNotable)) {
            return;
        }
        Long assignerId = task.getCreatedBy().getId();
        if (assignerId.equals(actor.getId())) {
            return;
        }
        String title = statusNotable ? "Công việc cần bạn xem xét" : "Có phản hồi mới trong công việc";
        String content = "\"%s\" (người thực hiện: %s) — trạng thái hiện tại: %s."
                .formatted(task.getTitle(), assignment.getAssignee().getFullName(), target);
        notificationService.notify(assignerId, Notification.NotificationType.TASK_COMMENT, title, content);
    }

    /** A2: người giao việc từ chối kết quả — người nhận việc nhận thông báo. */
    private void notifyAssigneeOnReject(Task task, TaskAssignment assignment, User actor) {
        Long assigneeId = assignment.getAssignee().getId();
        if (assigneeId.equals(actor.getId())) {
            return;
        }
        notificationService.notify(assigneeId, Notification.NotificationType.TASK_ASSIGNED,
                "Công việc bị trả lại để chỉnh sửa",
                "\"%s\" đã bị người giao việc từ chối, cần tiếp tục xử lý.".formatted(task.getTitle()));
    }

    /**
     * SDD (đã cập nhật theo UC-07 A3): task COMPLETED khi MỌI phân công CÒN
     * HIỆU LỰC (bỏ qua DECLINED) = COMPLETED. Phân công DECLINED không tính
     * vào điều kiện hoàn thành — nếu toàn bộ phân công đều DECLINED (chưa
     * giao lại) thì task GIỮ NGUYÊN trạng thái mở, chờ người giao giao lại
     * (reassign), không tự COMPLETED cũng không tự CANCELLED.
     */
    private void recomputeTaskStatus(Task task) {
        List<TaskAssignment> active = taskAssignmentRepository.findByTaskId(task.getId()).stream()
                .filter(a -> a.getStatus() != TaskAssignment.Status.DECLINED)
                .toList();
        boolean allActiveCompleted = !active.isEmpty()
                && active.stream().allMatch(a -> a.getStatus() == TaskAssignment.Status.COMPLETED);
        // Không tự hoàn thành task đã ở trạng thái kết thúc (COMPLETED/CANCELLED).
        boolean terminal = task.getStatus() == Task.Status.COMPLETED || task.getStatus() == Task.Status.CANCELLED;
        if (allActiveCompleted && !terminal) {
            task.setStatus(Task.Status.COMPLETED);
            task.setCompletedAt(OffsetDateTime.now());
            taskRepository.save(task);
        }
    }

    private String generateTaskCode() {
        OffsetDateTime now = OffsetDateTime.now();
        String prefix = "TSK-%d-%02d-".formatted(now.getYear(), now.getMonthValue());
        long count = taskRepository.countByTaskCodeStartingWith(prefix);
        return prefix + "%04d".formatted(count + 1);
    }

    private Set<String> roleCodesOf(Long userId) {
        return userRoleRepository.findByUserId(userId).stream()
                .map(ur -> ur.getRole().getCode())
                .collect(Collectors.toSet());
    }

    private User getUserOrThrow(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("error.task.accountNotFound", new Object[]{id}, "Không tìm thấy tài khoản id=" + id));
    }

    private Task getTaskOrThrow(Long id) {
        return taskRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("error.task.notFoundById", new Object[]{id}, "Không tìm thấy công việc id=" + id));
    }

    private void writeTaskHistory(Task task, User actor, TaskHistory.Action action) {
        TaskHistory history = new TaskHistory();
        history.setTask(task);
        history.setChangedBy(actor);
        history.setAction(action);
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("title", task.getTitle());
        snapshot.put("status", task.getStatus().name());
        history.setDetails(snapshot);
        taskHistoryRepository.save(history);
    }

    private void writeAssignmentHistory(TaskAssignment assignment, User actor, TaskAssignmentHistory.Action action) {
        TaskAssignmentHistory history = new TaskAssignmentHistory();
        history.setTaskAssignment(assignment);
        history.setChangedBy(actor);
        history.setAction(action);
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("assigneeId", assignment.getAssignee().getId());
        snapshot.put("status", assignment.getStatus().name());
        history.setDetails(snapshot);
        taskAssignmentHistoryRepository.save(history);
    }

    private TaskResponse toResponse(Task t) {
        return new TaskResponse(
                t.getId(), t.getTaskCode(), t.getTitle(), t.getDescription(), t.getCreatedBy().getId(),
                t.getCreatedBy().getFullName(), t.getDepartment() == null ? null : t.getDepartment().getId(),
                t.getTaskType().name(), t.getPriority().name(), t.getStatus().name(), t.getDueAt(), t.getCompletedAt(),
                t.getParentTask() == null ? null : t.getParentTask().getId(), t.getTags());
    }

    private TaskAssignmentResponse toResponse(TaskAssignment a) {
        return new TaskAssignmentResponse(
                a.getId(), a.getTask().getId(), a.getTask().getTitle(), a.getAssignee().getId(), a.getAssignee().getFullName(),
                a.getAssignedAt(), a.getStatus().name(), a.getProgressPercent(), a.getStartedAt(), a.getCompletedAt(),
                a.getDeclineReason());
    }

    private TaskAttachmentResponse toResponse(TaskAttachment a) {
        return new TaskAttachmentResponse(
                a.getId(), a.getTask().getId(), a.getFileUrl(), a.getFileName(),
                a.getUploadedBy() == null ? null : a.getUploadedBy().getId());
    }

    private TaskCommentResponse toResponse(TaskComment c) {
        return new TaskCommentResponse(
                c.getId(), c.getTask().getId(), c.getCommenter().getId(), c.getCommenter().getFullName(),
                c.getContent(), c.getAttachmentUrl(), c.getCreatedAt());
    }
}
