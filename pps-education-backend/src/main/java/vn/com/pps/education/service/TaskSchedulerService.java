package vn.com.pps.education.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.domain.Notification;
import vn.com.pps.education.domain.Task;
import vn.com.pps.education.domain.TaskAssignment;
import vn.com.pps.education.repository.SystemSettingRepository;
import vn.com.pps.education.repository.TaskAssignmentHistoryRepository;
import vn.com.pps.education.repository.TaskAssignmentRepository;
import vn.com.pps.education.repository.TaskAttachmentRepository;
import vn.com.pps.education.repository.TaskCommentRepository;
import vn.com.pps.education.repository.TaskHistoryRepository;
import vn.com.pps.education.repository.TaskRepository;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * UC-07 A1 (nhắc nhở sắp trễ hạn) + SDD "Cron job nightly set OVERDUE khi
 * quá hạn" (Task Management). Chạy 1 lần/đêm (01:00) — cả 2 việc dùng
 * chung 1 job vì cùng quét bảng tasks theo due_at.
 *
 * Ngưỡng "sắp trễ hạn" (A1) không được SDD/UC nêu số giờ cụ thể — đã xác
 * nhận với user: đọc từ system_settings key task.due_soon_reminder_hours
 * (migration V23), không hard-code.
 */
@Service
public class TaskSchedulerService {

    private static final Logger log = LoggerFactory.getLogger(TaskSchedulerService.class);
    private static final String DUE_SOON_SETTING_KEY = "task.due_soon_reminder_hours";
    private static final List<Task.Status> OPEN_STATUSES = List.of(Task.Status.OPEN, Task.Status.IN_PROGRESS);

    private final TaskRepository taskRepository;
    private final TaskAssignmentRepository taskAssignmentRepository;
    private final TaskAssignmentHistoryRepository taskAssignmentHistoryRepository;
    private final TaskCommentRepository taskCommentRepository;
    private final TaskAttachmentRepository taskAttachmentRepository;
    private final TaskHistoryRepository taskHistoryRepository;
    private final SystemSettingRepository systemSettingRepository;
    private final TaskSettingsService taskSettingsService;
    private final NotificationService notificationService;

    public TaskSchedulerService(TaskRepository taskRepository,
                                 TaskAssignmentRepository taskAssignmentRepository,
                                 TaskAssignmentHistoryRepository taskAssignmentHistoryRepository,
                                 TaskCommentRepository taskCommentRepository,
                                 TaskAttachmentRepository taskAttachmentRepository,
                                 TaskHistoryRepository taskHistoryRepository,
                                 SystemSettingRepository systemSettingRepository,
                                 TaskSettingsService taskSettingsService,
                                 NotificationService notificationService) {
        this.taskRepository = taskRepository;
        this.taskAssignmentRepository = taskAssignmentRepository;
        this.taskAssignmentHistoryRepository = taskAssignmentHistoryRepository;
        this.taskCommentRepository = taskCommentRepository;
        this.taskAttachmentRepository = taskAttachmentRepository;
        this.taskHistoryRepository = taskHistoryRepository;
        this.systemSettingRepository = systemSettingRepository;
        this.taskSettingsService = taskSettingsService;
        this.notificationService = notificationService;
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
        if (!overdue.isEmpty()) {
            log.info("TaskSchedulerService: đánh dấu OVERDUE {} task quá hạn.", overdue.size());
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
                notificationService.notify(assignment.getAssignee().getId(), Notification.NotificationType.TASK_ASSIGNED,
                        "Công việc sắp đến hạn",
                        "\"%s\" sẽ đến hạn lúc %s, hãy hoàn thành sớm.".formatted(task.getTitle(), task.getDueAt()));
            }
        }
    }
}
