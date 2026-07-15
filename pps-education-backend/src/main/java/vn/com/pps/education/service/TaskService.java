package vn.com.pps.education.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
import vn.com.pps.education.dto.CreateTaskRequest;
import vn.com.pps.education.dto.TaskAssignmentResponse;
import vn.com.pps.education.dto.TaskAttachmentResponse;
import vn.com.pps.education.dto.TaskCommentResponse;
import vn.com.pps.education.dto.TaskResponse;
import vn.com.pps.education.dto.UpdateAssignmentStatusRequest;
import vn.com.pps.education.exception.AssigneeOutsideDepartmentException;
import vn.com.pps.education.exception.InvalidTaskStatusTransitionException;
import vn.com.pps.education.exception.NotTaskParticipantException;
import vn.com.pps.education.exception.ResourceNotFoundException;
import vn.com.pps.education.repository.TaskAssignmentHistoryRepository;
import vn.com.pps.education.repository.TaskAssignmentRepository;
import vn.com.pps.education.repository.TaskAttachmentRepository;
import vn.com.pps.education.repository.TaskCommentRepository;
import vn.com.pps.education.repository.TaskHistoryRepository;
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
        this.notificationService = notificationService;
    }

    /** Main Flow bước 1-6, A2 (giao hàng loạt): tạo task + 1 task_assignment/người nhận, thông báo từng người. */
    @Transactional
    public TaskResponse createTask(CreateTaskRequest request, Long actorUserId) {
        User actor = getUserOrThrow(actorUserId);
        Employee actorEmployee = employeeRepository.findByUserId(actorUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Tài khoản chưa có hồ sơ nhân sự."));
        List<User> assignees = userRepository.findAllById(request.assigneeUserIds());
        if (assignees.size() != request.assigneeUserIds().size()) {
            throw new ResourceNotFoundException("Có người nhận việc không tồn tại trong danh sách assigneeUserIds.");
        }
        Map<Long, Employee> assigneeEmployeesByUserId = employeeRepository.findByUserIdIn(request.assigneeUserIds()).stream()
                .collect(Collectors.toMap(e -> e.getUser().getId(), e -> e));
        requireInDepartmentScope(actorUserId, actorEmployee, assignees, assigneeEmployeesByUserId);

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

    /** UC-07 Main Flow bước 1: không gian làm việc Kanban của người nhận việc. */
    @Transactional(readOnly = true)
    public List<TaskAssignmentResponse> listMyAssignments(Long actorUserId) {
        return taskAssignmentRepository.findByAssigneeIdOrderByIdDesc(actorUserId).stream().map(this::toResponse).toList();
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
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy phân công công việc id=" + assignmentId));
        Task task = assignment.getTask();
        User actor = getUserOrThrow(actorUserId);
        TaskAssignment.Status current = assignment.getStatus();
        TaskAssignment.Status target = TaskAssignment.Status.valueOf(request.status());

        boolean isAssignee = assignment.getAssignee().getId().equals(actorUserId);
        boolean isAssigner = task.getCreatedBy().getId().equals(actorUserId);
        if (!isAssignee && !isAssigner) {
            throw new NotTaskParticipantException(
                    "Tài khoản id=" + actorUserId + " không phải người giao hoặc người nhận công việc id=" + task.getId() + ".");
        }
        boolean validAsAssignee = isAssignee && ASSIGNEE_TRANSITIONS.getOrDefault(current, Set.of()).contains(target);
        boolean isAssignerReject = current == TaskAssignment.Status.PENDING_REVIEW && target == TaskAssignment.Status.IN_PROGRESS;
        boolean validAsAssigner = isAssigner && ASSIGNER_TRANSITIONS.getOrDefault(current, Set.of()).contains(target);
        if (!validAsAssignee && !validAsAssigner) {
            throw new InvalidTaskStatusTransitionException(
                    "Không thể chuyển phân công id=" + assignmentId + " từ " + current + " sang " + target + ".");
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

    /** Main Flow bước 3, A1: phạm vi phòng ban — trừ is_management=true và role OPS_MANAGER (toàn công ty). */
    private void requireInDepartmentScope(Long actorUserId, Employee actorEmployee, List<User> assignees,
                                           Map<Long, Employee> assigneeEmployeesByUserId) {
        boolean companyWide = actorEmployee.isManagement() && roleCodesOf(actorUserId).contains("OPS_MANAGER");
        if (companyWide) {
            return;
        }
        Long actorDeptId = actorEmployee.getDepartment() == null ? null : actorEmployee.getDepartment().getId();
        for (User assignee : assignees) {
            Employee assigneeEmployee = assigneeEmployeesByUserId.get(assignee.getId());
            Long assigneeDeptId = assigneeEmployee == null || assigneeEmployee.getDepartment() == null
                    ? null : assigneeEmployee.getDepartment().getId();
            if (actorDeptId == null || !actorDeptId.equals(assigneeDeptId)) {
                throw new AssigneeOutsideDepartmentException(
                        "Người nhận việc id=" + assignee.getId() + " không thuộc phòng ban của người giao id=" + actorUserId + ".");
            }
        }
    }

    private void requireParticipant(Task task, Long actorUserId) {
        if (task.getCreatedBy().getId().equals(actorUserId)) {
            return;
        }
        if (taskAssignmentRepository.findByTaskIdAndAssigneeId(task.getId(), actorUserId).isPresent()) {
            return;
        }
        throw new NotTaskParticipantException(
                "Tài khoản id=" + actorUserId + " không phải người giao hoặc người nhận công việc id=" + task.getId() + ".");
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

    /** SDD: khi tất cả task_assignments của task = COMPLETED → tasks.status = COMPLETED. */
    private void recomputeTaskStatus(Task task) {
        long notCompleted = taskAssignmentRepository.countByTaskIdAndStatusNot(task.getId(), TaskAssignment.Status.COMPLETED);
        if (notCompleted == 0 && task.getStatus() != Task.Status.COMPLETED) {
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
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản id=" + id));
    }

    private Task getTaskOrThrow(Long id) {
        return taskRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy công việc id=" + id));
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
