package vn.com.pps.education.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.domain.Department;
import vn.com.pps.education.domain.SystemSetting;
import vn.com.pps.education.domain.Task;
import vn.com.pps.education.domain.TaskAssignment;
import vn.com.pps.education.domain.User;
import vn.com.pps.education.repository.DepartmentRepository;
import vn.com.pps.education.repository.SystemSettingRepository;
import vn.com.pps.education.repository.TaskAssignmentRepository;
import vn.com.pps.education.repository.TaskRepository;
import vn.com.pps.education.repository.UserRepository;
import vn.com.pps.education.support.AbstractIntegrationTest;

import java.time.OffsetDateTime;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TaskSchedulerService — SDD cron nightly đặt OVERDUE + UC-07 A1 nhắc nhở sắp
 * trễ hạn. Gọi thẳng {@code runNightlyJob()} thay vì đợi cron trigger.
 */
@Transactional
class TaskSchedulerServiceTest extends AbstractIntegrationTest {

    private static final AtomicLong SEQ = new AtomicLong();

    @Autowired private TaskSchedulerService taskSchedulerService;
    @Autowired private TaskRepository taskRepository;
    @Autowired private TaskAssignmentRepository taskAssignmentRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private DepartmentRepository departmentRepository;
    @Autowired private SystemSettingRepository systemSettingRepository;
    @Autowired private ObjectMapper objectMapper;

    @Test
    void runNightlyJob_marksOverdueTasks() {
        Department dept = newDepartment();
        User creator = newUserInDept("overdue.creator", dept);
        User assignee = newUserInDept("overdue.assignee", dept);

        // Task with due_at in the past
        Task overdueTask = createTaskDirectly(creator, dept, "Quá hạn task",
                OffsetDateTime.now().minusDays(2), Task.Status.OPEN);
        createAssignmentDirectly(overdueTask, assignee);

        // Task with due_at in the future (should NOT be marked)
        Task futureTask = createTaskDirectly(creator, dept, "Chưa hạn task",
                OffsetDateTime.now().plusDays(5), Task.Status.OPEN);

        taskSchedulerService.runNightlyJob();

        Task reloadedOverdue = taskRepository.findById(overdueTask.getId()).orElseThrow();
        assertThat(reloadedOverdue.getStatus()).isEqualTo(Task.Status.OVERDUE);

        Task reloadedFuture = taskRepository.findById(futureTask.getId()).orElseThrow();
        assertThat(reloadedFuture.getStatus()).isEqualTo(Task.Status.OPEN);
    }

    @Test
    void runNightlyJob_doesNotMarkCompletedTasksAsOverdue() {
        Department dept = newDepartment();
        User creator = newUserInDept("completed.creator", dept);

        Task completedTask = createTaskDirectly(creator, dept, "Already done",
                OffsetDateTime.now().minusDays(1), Task.Status.COMPLETED);

        taskSchedulerService.runNightlyJob();

        Task reloaded = taskRepository.findById(completedTask.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(Task.Status.COMPLETED);
    }

    @Test
    void runNightlyJob_notifiesDueSoonTasks() {
        Department dept = newDepartment();
        User creator = newUserInDept("dueSoon.creator", dept);
        User assignee = newUserInDept("dueSoon.assignee", dept);

        // Ensure due_soon setting exists (24 hours default)
        ensureDueSoonSetting(24);

        // Task due in 12 hours (within 24h window → should trigger notification)
        Task dueSoonTask = createTaskDirectly(creator, dept, "Sắp hạn",
                OffsetDateTime.now().plusHours(12), Task.Status.OPEN);
        createAssignmentDirectly(dueSoonTask, assignee);

        // Should not throw — notifications dispatched internally
        taskSchedulerService.runNightlyJob();

        // Task status stays OPEN (not overdue — due_at is in the future)
        Task reloaded = taskRepository.findById(dueSoonTask.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(Task.Status.OPEN);
    }

    @Test
    void runNightlyJob_deletesCancelledTasksPastRetention() {
        Department dept = newDepartment();
        User creator = newUserInDept("cleanup.creator", dept);
        User assignee = newUserInDept("cleanup.assignee", dept);

        // CANCELLED 10 ngày trước (quá hạn giữ mặc định 7 ngày) → xóa cứng cả assignment con.
        Task oldCancelled = createTaskDirectly(creator, dept, "Hủy lâu", null, Task.Status.CANCELLED);
        oldCancelled.setCancelledAt(OffsetDateTime.now().minusDays(10));
        taskRepository.save(oldCancelled);
        createAssignmentDirectly(oldCancelled, assignee);

        // CANCELLED hôm nay → còn trong hạn giữ, KHÔNG bị xóa.
        Task recentCancelled = createTaskDirectly(creator, dept, "Hủy gần đây", null, Task.Status.CANCELLED);
        recentCancelled.setCancelledAt(OffsetDateTime.now());
        taskRepository.save(recentCancelled);

        taskSchedulerService.runNightlyJob();

        assertThat(taskRepository.findById(oldCancelled.getId())).isEmpty();
        assertThat(taskRepository.findById(recentCancelled.getId())).isPresent();
    }

    // ===================== Helpers =====================

    private Task createTaskDirectly(User creator, Department dept, String title, OffsetDateTime dueAt, Task.Status status) {
        Task task = new Task();
        task.setTaskCode("TSK-TEST-" + SEQ.incrementAndGet());
        task.setTitle(title);
        task.setCreatedBy(creator);
        task.setDepartment(dept);
        task.setDueAt(dueAt);
        task.setStatus(status);
        return taskRepository.save(task);
    }

    private TaskAssignment createAssignmentDirectly(Task task, User assignee) {
        TaskAssignment assignment = new TaskAssignment();
        assignment.setTask(task);
        assignment.setAssignee(assignee);
        return taskAssignmentRepository.save(assignment);
    }

    private void ensureDueSoonSetting(int hours) {
        if (systemSettingRepository.findBySettingKey("task.due_soon_reminder_hours").isEmpty()) {
            SystemSetting setting = new SystemSetting();
            setting.setSettingKey("task.due_soon_reminder_hours");
            setting.setSettingValue(objectMapper.valueToTree(hours));
            setting.setCategory("task");
            systemSettingRepository.save(setting);
        }
    }

    private Department newDepartment() {
        Department department = new Department();
        department.setCode("DEPT-SCHED-" + SEQ.incrementAndGet());
        department.setName("Scheduler Test Dept " + SEQ.get());
        return departmentRepository.save(department);
    }

    // dept không còn set trực tiếp trên User (đã chuyển sang Employee) — tham
    // số giữ lại vì Task.department (đối tượng đang test) vẫn set độc lập qua
    // createTaskDirectly, không đọc lại từ User.
    private User newUserInDept(String prefix, Department dept) {
        User user = new User();
        long seq = SEQ.incrementAndGet();
        user.setUsername(prefix + "." + seq);
        user.setEmail(prefix + "." + seq + "@pps.edu.vn");
        user.setFullName("Test " + prefix);
        user.setStatus(User.Status.ACTIVE);
        return userRepository.save(user);
    }
}
