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
import vn.com.pps.education.repository.TaskAssignmentRepository;
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
    private final SystemSettingRepository systemSettingRepository;
    private final NotificationService notificationService;

    public TaskSchedulerService(TaskRepository taskRepository,
                                 TaskAssignmentRepository taskAssignmentRepository,
                                 SystemSettingRepository systemSettingRepository,
                                 NotificationService notificationService) {
        this.taskRepository = taskRepository;
        this.taskAssignmentRepository = taskAssignmentRepository;
        this.systemSettingRepository = systemSettingRepository;
        this.notificationService = notificationService;
    }

    @Scheduled(cron = "0 0 1 * * *")
    @Transactional
    public void runNightlyJob() {
        OffsetDateTime now = OffsetDateTime.now();
        markOverdue(now);
        notifyDueSoon(now);
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
