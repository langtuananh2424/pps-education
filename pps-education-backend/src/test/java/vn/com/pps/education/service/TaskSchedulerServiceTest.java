package vn.com.pps.education.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.domain.Department;
import vn.com.pps.education.domain.Employee;
import vn.com.pps.education.domain.Notification;
import vn.com.pps.education.domain.SystemSetting;
import vn.com.pps.education.domain.Task;
import vn.com.pps.education.domain.TaskAssignment;
import vn.com.pps.education.domain.User;
import vn.com.pps.education.repository.DepartmentRepository;
import vn.com.pps.education.repository.EmployeeRepository;
import vn.com.pps.education.repository.NotificationRepository;
import vn.com.pps.education.repository.SystemSettingRepository;
import vn.com.pps.education.repository.TaskAssignmentRepository;
import vn.com.pps.education.repository.TaskRepository;
import vn.com.pps.education.repository.UserRepository;
import vn.com.pps.education.support.AbstractIntegrationTest;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
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
    @Autowired private EmployeeRepository employeeRepository;
    @Autowired private NotificationRepository notificationRepository;

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

    @Test
    void runNightlyJob_overdue_notifiesDepartmentHeadOnceWithUnfinishedAssignees() {
        // Bổ sung 2026-09-29: task vừa quá hạn -> báo trưởng phòng của người nhận việc chưa hoàn thành
        Department dept = newDepartment();
        User head = newUserInDept("overdue.head", dept);
        setHead(dept, head);
        User creator = newUserInDept("overdue.head.creator", dept);
        User late1 = newEmployeeInDept("overdue.late1", dept);
        User late2 = newEmployeeInDept("overdue.late2", dept);
        User done = newEmployeeInDept("overdue.done", dept);

        Task task = createTaskDirectly(creator, dept, "Nộp báo cáo tháng", OffsetDateTime.now().minusHours(3), Task.Status.IN_PROGRESS);
        createAssignmentDirectly(task, late1);
        createAssignmentDirectly(task, late2);
        TaskAssignment completed = createAssignmentDirectly(task, done);
        completed.setStatus(TaskAssignment.Status.COMPLETED);
        taskAssignmentRepository.save(completed);

        taskSchedulerService.runNightlyJob();
        taskSchedulerService.runNightlyJob(); // đã OVERDUE -> lần 2 không gửi lặp

        List<Notification> notifs = overdueAlertsOf(head);
        assertThat(notifs).hasSize(1);
        Notification n = notifs.get(0);
        assertThat(n.getTitle()).contains("Nộp báo cáo tháng");
        assertThat(n.getContent()).contains(late1.getFullName()).contains(late2.getFullName()).doesNotContain(done.getFullName());
        assertThat(n.getEntityId()).isEqualTo(task.getId());
    }

    @Test
    void runNightlyJob_overdue_skipsWhenDepartmentHasNoHead() {
        Department dept = newDepartment();
        User creator = newUserInDept("overdue.nohead.creator", dept);
        User assignee = newEmployeeInDept("overdue.nohead.assignee", dept);
        Task task = createTaskDirectly(creator, dept, "Không trưởng phòng", OffsetDateTime.now().minusDays(1), Task.Status.OPEN);
        createAssignmentDirectly(task, assignee);

        taskSchedulerService.runNightlyJob();

        assertThat(taskRepository.findById(task.getId()).orElseThrow().getStatus()).isEqualTo(Task.Status.OVERDUE);
        assertThat(overdueAlertsOf(assignee)).isEmpty();
    }

    @Test
    void runNightlyJob_overdue_headNotAlertedAboutOwnAssignment() {
        Department dept = newDepartment();
        User head = newEmployeeInDept("overdue.self.head", dept);
        setHead(dept, head);
        Task task = createTaskDirectly(head, dept, "Việc của trưởng phòng", OffsetDateTime.now().minusDays(1), Task.Status.OPEN);
        createAssignmentDirectly(task, head);

        taskSchedulerService.runNightlyJob();

        assertThat(overdueAlertsOf(head)).isEmpty();
    }

    // ===================== Helpers =====================

    private List<Notification> overdueAlertsOf(User user) {
        return notificationRepository.findByRecipientUserIdOrderByCreatedAtDesc(user.getId(), PageRequest.of(0, 50))
                .getContent().stream()
                .filter(n -> n.getNotificationType() == Notification.NotificationType.TASK_ASSIGNED)
                .filter(n -> n.getTitle().startsWith("Công việc quá hạn"))
                .toList();
    }

    private void setHead(Department dept, User head) {
        dept.setHeadUser(head);
        departmentRepository.save(dept);
    }

    private User newEmployeeInDept(String prefix, Department dept) {
        User user = newUserInDept(prefix, dept);
        Employee employee = new Employee();
        employee.setUser(user);
        employee.setEmployeeCode("EMP-SCHED-" + SEQ.incrementAndGet());
        employee.setDateOfBirth(LocalDate.of(1995, 1, 1));
        employee.setEmployeeType(Employee.EmployeeType.STAFF);
        employee.setHireDate(LocalDate.now().minusYears(1));
        employee.setDepartment(dept);
        employeeRepository.save(employee);
        return user;
    }

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
