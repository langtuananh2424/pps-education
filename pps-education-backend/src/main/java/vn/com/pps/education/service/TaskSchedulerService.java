package vn.com.pps.education.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.domain.Department;
import vn.com.pps.education.domain.Employee;
import vn.com.pps.education.domain.Notification;
import vn.com.pps.education.domain.Task;
import vn.com.pps.education.domain.TaskAssignment;
import vn.com.pps.education.domain.User;
import vn.com.pps.education.repository.EmployeeRepository;
import vn.com.pps.education.repository.SystemSettingRepository;
import vn.com.pps.education.repository.TaskAssignmentHistoryRepository;
import vn.com.pps.education.repository.TaskAssignmentRepository;
import vn.com.pps.education.repository.TaskAttachmentRepository;
import vn.com.pps.education.repository.TaskCommentRepository;
import vn.com.pps.education.repository.TaskHistoryRepository;
import vn.com.pps.education.repository.TaskRepository;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * UC-07 A1 (nhắc nhở sắp trễ hạn) + SDD "Cron job nightly set OVERDUE khi
 * quá hạn" (Task Management). Chạy 1 lần/đêm (01:00) — cả 2 việc dùng
 * chung 1 job vì cùng quét bảng tasks theo due_at.
 *
 * Ngưỡng "sắp trễ hạn" (A1) không được SDD/UC nêu số giờ cụ thể — đã xác
 * nhận với user: đọc từ system_settings key task.due_soon_reminder_hours
 * (migration V23), không hard-code.
 *
 * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-29: đúng lúc
 * 1 task chuyển sang OVERDUE, báo trưởng phòng (departments.head_user_id —
 * cùng khái niệm "trưởng phòng" TaskService dùng cho overview/giao việc)
 * của từng người nhận việc chưa hoàn thành, để Trưởng phòng đào tạo theo
 * dõi được cấp dưới trễ hạn. Chỉ gửi 1 lần (lúc chuyển trạng thái), gộp
 * mọi người nhận việc cùng phòng vào 1 thông báo; bỏ qua khi người nhận
 * việc chính là trưởng phòng.
 */
@Service
public class TaskSchedulerService {

    private static final Logger log = LoggerFactory.getLogger(TaskSchedulerService.class);
    private static final String DUE_SOON_SETTING_KEY = "task.due_soon_reminder_hours";
    private static final List<Task.Status> OPEN_STATUSES = List.of(Task.Status.OPEN, Task.Status.IN_PROGRESS);
    private static final DateTimeFormatter DUE_FMT = DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy");

    private final TaskRepository taskRepository;
    private final TaskAssignmentRepository taskAssignmentRepository;
    private final TaskAssignmentHistoryRepository taskAssignmentHistoryRepository;
    private final TaskCommentRepository taskCommentRepository;
    private final TaskAttachmentRepository taskAttachmentRepository;
    private final TaskHistoryRepository taskHistoryRepository;
    private final SystemSettingRepository systemSettingRepository;
    private final TaskSettingsService taskSettingsService;
    private final NotificationService notificationService;
    private final EmployeeRepository employeeRepository;

    public TaskSchedulerService(TaskRepository taskRepository,
                                 TaskAssignmentRepository taskAssignmentRepository,
                                 TaskAssignmentHistoryRepository taskAssignmentHistoryRepository,
                                 TaskCommentRepository taskCommentRepository,
                                 TaskAttachmentRepository taskAttachmentRepository,
                                 TaskHistoryRepository taskHistoryRepository,
                                 SystemSettingRepository systemSettingRepository,
                                 TaskSettingsService taskSettingsService,
                                 NotificationService notificationService,
                                 EmployeeRepository employeeRepository) {
        this.taskRepository = taskRepository;
        this.taskAssignmentRepository = taskAssignmentRepository;
        this.taskAssignmentHistoryRepository = taskAssignmentHistoryRepository;
        this.taskCommentRepository = taskCommentRepository;
        this.taskAttachmentRepository = taskAttachmentRepository;
        this.taskHistoryRepository = taskHistoryRepository;
        this.systemSettingRepository = systemSettingRepository;
        this.taskSettingsService = taskSettingsService;
        this.notificationService = notificationService;
        this.employeeRepository = employeeRepository;
    }

    @Scheduled(cron = "0 0 1 * * *")
    @Transactional
    public void runNightlyJob() {
        OffsetDateTime now = OffsetDateTime.now();
        markOverdue(now);
        notifyDueSoon(now);
        cleanupCancelled(now);
    }

    /**
     * UC-06/07 (bổ sung): xóa cứng task CANCELLED đã quá hạn giữ
     * task.cancelled_retention_days ngày (kể từ cancelled_at). Xóa con theo
     * đúng thứ tự khóa ngoại: task_assignments_history → task_assignments →
     * task_comments → task_attachments → tasks_history → tasks.
     */
    private void cleanupCancelled(OffsetDateTime now) {
        int retentionDays = taskSettingsService.cancelledRetentionDays();
        OffsetDateTime cutoff = now.minusDays(retentionDays);
        List<Task> stale = taskRepository.findByStatusAndCancelledAtBefore(Task.Status.CANCELLED, cutoff);
        for (Task task : stale) {
            Long taskId = task.getId();
            taskAssignmentHistoryRepository.deleteByTaskAssignment_Task_Id(taskId);
            taskAssignmentRepository.deleteByTaskId(taskId);
            taskCommentRepository.deleteByTaskId(taskId);
            taskAttachmentRepository.deleteByTaskId(taskId);
            taskHistoryRepository.deleteByTaskId(taskId);
            taskRepository.delete(task);
        }
        if (!stale.isEmpty()) {
            log.info("TaskSchedulerService: xóa cứng {} task CANCELLED quá {} ngày.", stale.size(), retentionDays);
        }
    }

    /** SDD: "Cron job nightly set OVERDUE khi quá hạn". */
    private void markOverdue(OffsetDateTime now) {
        List<Task> overdue = taskRepository.findOverdue(now, OPEN_STATUSES);
        for (Task task : overdue) {
            task.setStatus(Task.Status.OVERDUE);
        }
        taskRepository.saveAll(overdue);
        for (Task task : overdue) {
            notifyDepartmentHeadsOfOverdue(task);
        }
        if (!overdue.isEmpty()) {
            log.info("TaskSchedulerService: đánh dấu OVERDUE {} task quá hạn.", overdue.size());
        }
    }

    /** Bổ sung 2026-09-29: báo trưởng phòng của người nhận việc chưa hoàn thành khi task vừa quá hạn. */
    private void notifyDepartmentHeadsOfOverdue(Task task) {
        Map<Long, List<String>> assigneeNamesByHeadId = new LinkedHashMap<>();
        for (TaskAssignment assignment : taskAssignmentRepository.findByTaskId(task.getId())) {
            if (assignment.getStatus() == TaskAssignment.Status.COMPLETED
                    || assignment.getStatus() == TaskAssignment.Status.DECLINED) {
                continue;
            }
            User assignee = assignment.getAssignee();
            User head = employeeRepository.findByUserId(assignee.getId())
                    .map(Employee::getDepartment)
                    .map(Department::getHeadUser)
                    .orElse(null);
            if (head == null || head.getId().equals(assignee.getId()) || head.getStatus() != User.Status.ACTIVE) {
                continue;
            }
            assigneeNamesByHeadId.computeIfAbsent(head.getId(), k -> new ArrayList<>()).add(assignee.getFullName());
        }
        String due = task.getDueAt() == null ? "" : DUE_FMT.format(task.getDueAt().atZoneSameInstant(ZoneId.systemDefault()));
        for (Map.Entry<Long, List<String>> e : assigneeNamesByHeadId.entrySet()) {
            String names = String.join(", ", e.getValue());
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("action", "OVERDUE_HEAD_ALERT");
            metadata.put("taskTitle", task.getTitle());
            metadata.put("dueAt", task.getDueAt());
            metadata.put("assigneeNames", names);
            notificationService.notify(e.getKey(), Notification.NotificationType.TASK_ASSIGNED,
                    "Công việc quá hạn: " + task.getTitle(),
                    "\"%s\" đã quá hạn (%s) — chưa hoàn thành: %s.".formatted(task.getTitle(), due, names),
                    metadata, "TASK", task.getId(), Notification.Priority.HIGH, null);
        }
    }

    /** UC-07 A1: sắp đến hạn nhưng chưa Hoàn thành — nhắc nhở từng người nhận việc chưa COMPLETED. */
    private void notifyDueSoon(OffsetDateTime now) {
        int hours = systemSettingRepository.findBySettingKey(DUE_SOON_SETTING_KEY)
                .map(s -> s.getSettingValue().asInt())
                .orElse(24);
        OffsetDateTime threshold = now.plusHours(hours);
        List<Task> dueSoon = taskRepository.findDueSoon(now, threshold, OPEN_STATUSES);
        for (Task task : dueSoon) {
            List<TaskAssignment> assignments = taskAssignmentRepository.findByTaskId(task.getId());
            for (TaskAssignment assignment : assignments) {
                if (assignment.getStatus() == TaskAssignment.Status.COMPLETED
                        || assignment.getStatus() == TaskAssignment.Status.DECLINED) {
                    continue;
                }
                Map<String, Object> metadata = new LinkedHashMap<>();
                metadata.put("action", "DUE_SOON");
                metadata.put("taskTitle", task.getTitle());
                metadata.put("dueAt", task.getDueAt());
                notificationService.notify(assignment.getAssignee().getId(), Notification.NotificationType.TASK_ASSIGNED,
                        "Công việc sắp đến hạn",
                        "\"%s\" sẽ đến hạn lúc %s, hãy hoàn thành sớm.".formatted(task.getTitle(), task.getDueAt()),
                        metadata, "TASK", task.getId(), Notification.Priority.NORMAL, null);
            }
        }
    }
}
