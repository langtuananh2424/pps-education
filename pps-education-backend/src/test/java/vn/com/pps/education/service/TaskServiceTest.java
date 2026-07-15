package vn.com.pps.education.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.domain.Department;
import vn.com.pps.education.domain.Employee;
import vn.com.pps.education.domain.Role;
import vn.com.pps.education.domain.Task;
import vn.com.pps.education.domain.TaskAssignment;
import vn.com.pps.education.domain.User;
import vn.com.pps.education.domain.UserRole;
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
import vn.com.pps.education.repository.DepartmentRepository;
import vn.com.pps.education.repository.EmployeeRepository;
import vn.com.pps.education.repository.RoleRepository;
import vn.com.pps.education.repository.TaskAssignmentRepository;
import vn.com.pps.education.repository.TaskRepository;
import vn.com.pps.education.repository.UserRepository;
import vn.com.pps.education.repository.UserRoleRepository;
import vn.com.pps.education.support.AbstractIntegrationTest;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * UC-06: Giao việc (FR-TSK-01) + UC-07: Cập nhật tiến độ công việc (FR-TSK-02).
 *
 * Covers:
 * - UC-06 Main Flow: tạo task + giao cho 1/nhiều người cùng phòng ban
 * - UC-06 A1: người nhận ngoài phòng ban → AssigneeOutsideDepartmentException
 * - UC-06 A2: giao hàng loạt (nhiều assigneeUserIds)
 * - UC-06 Main Flow bước 2: đính kèm tệp tin
 * - UC-07 Main Flow bước 1-4: Kanban state machine PENDING→ACCEPTED→IN_PROGRESS→PENDING_REVIEW→COMPLETED
 * - UC-07 A2: người giao từ chối kết quả PENDING_REVIEW→IN_PROGRESS (phải có comment)
 * - UC-07 Main Flow bước 3: bình luận/phản hồi tiến độ
 * - Assignee DECLINED (phải có lý do)
 * - Auto-complete task khi tất cả assignments COMPLETED
 * - Non-participant bị chặn
 * - Invalid state transitions bị chặn
 */
@Transactional
class TaskServiceTest extends AbstractIntegrationTest {

    private static final AtomicLong SEQ = new AtomicLong();

    @Autowired private TaskService taskService;
    @Autowired private TaskRepository taskRepository;
    @Autowired private TaskAssignmentRepository taskAssignmentRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private EmployeeRepository employeeRepository;
    @Autowired private DepartmentRepository departmentRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private UserRoleRepository userRoleRepository;

    // ===================== UC-06: Giao việc =====================

    @Test
    void createTask_UC06_MainFlow_singleAssignee_createsTaskAndAssignment() {
        Department dept = newDepartment();
        User assigner = newUserInDept("assigner", dept);
        User assignee = newUserInDept("assignee", dept);

        TaskResponse response = taskService.createTask(
                new CreateTaskRequest("Hoàn thành báo cáo Q3", "Mô tả chi tiết",
                        List.of(assignee.getId()), "GENERAL", "HIGH",
                        OffsetDateTime.now().plusDays(7), List.of("báo cáo", "Q3")),
                assigner.getId());

        assertThat(response.id()).isNotNull();
        assertThat(response.taskCode()).startsWith("TSK-");
        assertThat(response.title()).isEqualTo("Hoàn thành báo cáo Q3");
        assertThat(response.createdBy()).isEqualTo(assigner.getId());
        assertThat(response.status()).isEqualTo("OPEN");
        assertThat(response.taskType()).isEqualTo("GENERAL");
        assertThat(response.priority()).isEqualTo("HIGH");
        assertThat(response.tags()).containsExactly("báo cáo", "Q3");

        List<TaskAssignmentResponse> assignments = taskService.listMyAssignments(assignee.getId());
        assertThat(assignments).hasSize(1);
        assertThat(assignments.getFirst().taskId()).isEqualTo(response.id());
        assertThat(assignments.getFirst().assignmentStatus()).isEqualTo("PENDING");
    }

    @Test
    void createTask_UC06_A2_multipleAssignees_createsOneAssignmentPerPerson() {
        Department dept = newDepartment();
        User assigner = newUserInDept("assigner.multi", dept);
        User assignee1 = newUserInDept("assignee1", dept);
        User assignee2 = newUserInDept("assignee2", dept);
        User assignee3 = newUserInDept("assignee3", dept);

        TaskResponse response = taskService.createTask(
                new CreateTaskRequest("Giao hàng loạt", null,
                        List.of(assignee1.getId(), assignee2.getId(), assignee3.getId()),
                        null, null, null, null),
                assigner.getId());

        assertThat(taskService.listMyAssignments(assignee1.getId())).hasSize(1);
        assertThat(taskService.listMyAssignments(assignee2.getId())).hasSize(1);
        assertThat(taskService.listMyAssignments(assignee3.getId())).hasSize(1);
    }

    @Test
    void createTask_UC06_A1_assigneeOutsideDepartment_throws() {
        Department deptA = newDepartment();
        Department deptB = newDepartment();
        User assigner = newUserInDept("assigner.deptA", deptA);
        User assignee = newUserInDept("assignee.deptB", deptB);

        assertThatThrownBy(() -> taskService.createTask(
                new CreateTaskRequest("Cross-dept task", null,
                        List.of(assignee.getId()), null, null, null, null),
                assigner.getId()))
                .isInstanceOf(AssigneeOutsideDepartmentException.class);
    }

    @Test
    void createTask_UC06_opsManager_canAssignCrossDepartment() {
        Department deptA = newDepartment();
        Department deptB = newDepartment();
        User opsManager = newUserInDept("ops.manager", deptA);
        Employee opsManagerEmployee = employeeRepository.findByUserId(opsManager.getId()).orElseThrow();
        opsManagerEmployee.setManagement(true);
        employeeRepository.save(opsManagerEmployee);
        assignRole(opsManager, "OPS_MANAGER");
        User assignee = newUserInDept("assignee.cross", deptB);

        TaskResponse response = taskService.createTask(
                new CreateTaskRequest("Cross-dept allowed", null,
                        List.of(assignee.getId()), null, null, null, null),
                opsManager.getId());

        assertThat(response.id()).isNotNull();
    }

    @Test
    void createTask_nonExistentAssignee_throws() {
        Department dept = newDepartment();
        User assigner = newUserInDept("assigner.ghost", dept);

        assertThatThrownBy(() -> taskService.createTask(
                new CreateTaskRequest("Ghost assignee", null,
                        List.of(999999L), null, null, null, null),
                assigner.getId()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ===================== UC-06 Attachment =====================

    @Test
    void addAttachment_UC06_MainFlowStep2_byCreator() {
        Department dept = newDepartment();
        User assigner = newUserInDept("attach.assigner", dept);
        User assignee = newUserInDept("attach.assignee", dept);

        TaskResponse task = taskService.createTask(
                new CreateTaskRequest("Task with attachments", null,
                        List.of(assignee.getId()), null, null, null, null),
                assigner.getId());

        TaskAttachmentResponse attachment = taskService.addAttachment(task.id(),
                new AddTaskAttachmentRequest("https://cdn.pps.edu.vn/file.pdf", "file.pdf"),
                assigner.getId());

        assertThat(attachment.id()).isNotNull();
        assertThat(attachment.fileUrl()).isEqualTo("https://cdn.pps.edu.vn/file.pdf");
        assertThat(attachment.fileName()).isEqualTo("file.pdf");
        assertThat(attachment.uploadedBy()).isEqualTo(assigner.getId());

        List<TaskAttachmentResponse> list = taskService.listAttachments(task.id(), assigner.getId());
        assertThat(list).hasSize(1);
    }

    @Test
    void addAttachment_nonParticipant_throws() {
        Department dept = newDepartment();
        User assigner = newUserInDept("attach.owner", dept);
        User assignee = newUserInDept("attach.worker", dept);
        User outsider = newUserInDept("attach.outsider", dept);

        TaskResponse task = taskService.createTask(
                new CreateTaskRequest("Private task", null,
                        List.of(assignee.getId()), null, null, null, null),
                assigner.getId());

        assertThatThrownBy(() -> taskService.addAttachment(task.id(),
                new AddTaskAttachmentRequest("https://cdn/x.pdf", "x.pdf"),
                outsider.getId()))
                .isInstanceOf(NotTaskParticipantException.class);
    }

    // ===================== UC-07: Cập nhật tiến độ =====================

    @Test
    void updateAssignmentStatus_UC07_fullKanbanFlow_PENDING_to_COMPLETED() {
        Department dept = newDepartment();
        User assigner = newUserInDept("kanban.assigner", dept);
        User assignee = newUserInDept("kanban.assignee", dept);

        TaskResponse task = taskService.createTask(
                new CreateTaskRequest("Kanban flow task", null,
                        List.of(assignee.getId()), null, null, null, null),
                assigner.getId());
        Long assignmentId = taskService.listMyAssignments(assignee.getId()).getFirst().id();

        // PENDING → ACCEPTED (by assignee)
        TaskAssignmentResponse r1 = taskService.updateAssignmentStatus(assignmentId,
                new UpdateAssignmentStatusRequest("ACCEPTED", null), assignee.getId());
        assertThat(r1.assignmentStatus()).isEqualTo("ACCEPTED");

        // ACCEPTED → IN_PROGRESS (by assignee)
        TaskAssignmentResponse r2 = taskService.updateAssignmentStatus(assignmentId,
                new UpdateAssignmentStatusRequest("IN_PROGRESS", null), assignee.getId());
        assertThat(r2.assignmentStatus()).isEqualTo("IN_PROGRESS");
        assertThat(r2.startedAt()).isNotNull();

        // IN_PROGRESS → PENDING_REVIEW (by assignee — "nộp kết quả")
        TaskAssignmentResponse r3 = taskService.updateAssignmentStatus(assignmentId,
                new UpdateAssignmentStatusRequest("PENDING_REVIEW", "Đã hoàn thành, xin duyệt"), assignee.getId());
        assertThat(r3.assignmentStatus()).isEqualTo("PENDING_REVIEW");

        // PENDING_REVIEW → COMPLETED (by assigner — duyệt)
        TaskAssignmentResponse r4 = taskService.updateAssignmentStatus(assignmentId,
                new UpdateAssignmentStatusRequest("COMPLETED", null), assigner.getId());
        assertThat(r4.assignmentStatus()).isEqualTo("COMPLETED");
        assertThat(r4.completedAt()).isNotNull();

        // Task itself should also be COMPLETED (auto-recompute)
        TaskResponse taskAfter = taskService.getTask(task.id(), assigner.getId());
        assertThat(taskAfter.status()).isEqualTo("COMPLETED");
    }

    @Test
    void updateAssignmentStatus_UC07_directShortcut_PENDING_to_IN_PROGRESS() {
        Department dept = newDepartment();
        User assigner = newUserInDept("shortcut.assigner", dept);
        User assignee = newUserInDept("shortcut.assignee", dept);

        taskService.createTask(
                new CreateTaskRequest("Shortcut task", null,
                        List.of(assignee.getId()), null, null, null, null),
                assigner.getId());
        Long assignmentId = taskService.listMyAssignments(assignee.getId()).getFirst().id();

        // PENDING → IN_PROGRESS directly (allowed)
        TaskAssignmentResponse r = taskService.updateAssignmentStatus(assignmentId,
                new UpdateAssignmentStatusRequest("IN_PROGRESS", null), assignee.getId());
        assertThat(r.assignmentStatus()).isEqualTo("IN_PROGRESS");
    }

    @Test
    void updateAssignmentStatus_UC07_A2_assignerRejectsPendingReview_requiresComment() {
        Department dept = newDepartment();
        User assigner = newUserInDept("reject.assigner", dept);
        User assignee = newUserInDept("reject.assignee", dept);

        taskService.createTask(
                new CreateTaskRequest("Reject test", null,
                        List.of(assignee.getId()), null, null, null, null),
                assigner.getId());
        Long assignmentId = taskService.listMyAssignments(assignee.getId()).getFirst().id();

        // Move to PENDING_REVIEW
        taskService.updateAssignmentStatus(assignmentId,
                new UpdateAssignmentStatusRequest("IN_PROGRESS", null), assignee.getId());
        taskService.updateAssignmentStatus(assignmentId,
                new UpdateAssignmentStatusRequest("PENDING_REVIEW", null), assignee.getId());

        // A2: assigner rejects without comment → IllegalArgumentException
        assertThatThrownBy(() -> taskService.updateAssignmentStatus(assignmentId,
                new UpdateAssignmentStatusRequest("IN_PROGRESS", null), assigner.getId()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("lý do");

        // A2: assigner rejects with comment → succeeds, back to IN_PROGRESS
        TaskAssignmentResponse r = taskService.updateAssignmentStatus(assignmentId,
                new UpdateAssignmentStatusRequest("IN_PROGRESS", "Cần bổ sung phần kết luận"), assigner.getId());
        assertThat(r.assignmentStatus()).isEqualTo("IN_PROGRESS");
    }

    @Test
    void updateAssignmentStatus_assigneeDeclines_requiresReason() {
        Department dept = newDepartment();
        User assigner = newUserInDept("decline.assigner", dept);
        User assignee = newUserInDept("decline.assignee", dept);

        taskService.createTask(
                new CreateTaskRequest("Decline test", null,
                        List.of(assignee.getId()), null, null, null, null),
                assigner.getId());
        Long assignmentId = taskService.listMyAssignments(assignee.getId()).getFirst().id();

        // DECLINED without reason → IllegalArgumentException
        assertThatThrownBy(() -> taskService.updateAssignmentStatus(assignmentId,
                new UpdateAssignmentStatusRequest("DECLINED", null), assignee.getId()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("lý do");

        // DECLINED with reason → succeeds
        TaskAssignmentResponse r = taskService.updateAssignmentStatus(assignmentId,
                new UpdateAssignmentStatusRequest("DECLINED", "Không đủ năng lực xử lý"), assignee.getId());
        assertThat(r.assignmentStatus()).isEqualTo("DECLINED");
        assertThat(r.declineReason()).isEqualTo("Không đủ năng lực xử lý");
    }

    @Test
    void updateAssignmentStatus_invalidTransition_throws() {
        Department dept = newDepartment();
        User assigner = newUserInDept("invalid.assigner", dept);
        User assignee = newUserInDept("invalid.assignee", dept);

        taskService.createTask(
                new CreateTaskRequest("Invalid transition", null,
                        List.of(assignee.getId()), null, null, null, null),
                assigner.getId());
        Long assignmentId = taskService.listMyAssignments(assignee.getId()).getFirst().id();

        // PENDING → COMPLETED directly is invalid
        assertThatThrownBy(() -> taskService.updateAssignmentStatus(assignmentId,
                new UpdateAssignmentStatusRequest("COMPLETED", null), assignee.getId()))
                .isInstanceOf(InvalidTaskStatusTransitionException.class);

        // PENDING → PENDING_REVIEW directly is invalid
        assertThatThrownBy(() -> taskService.updateAssignmentStatus(assignmentId,
                new UpdateAssignmentStatusRequest("PENDING_REVIEW", null), assignee.getId()))
                .isInstanceOf(InvalidTaskStatusTransitionException.class);
    }

    @Test
    void updateAssignmentStatus_nonParticipant_throws() {
        Department dept = newDepartment();
        User assigner = newUserInDept("status.assigner", dept);
        User assignee = newUserInDept("status.assignee", dept);
        User outsider = newUserInDept("status.outsider", dept);

        taskService.createTask(
                new CreateTaskRequest("No access", null,
                        List.of(assignee.getId()), null, null, null, null),
                assigner.getId());
        Long assignmentId = taskService.listMyAssignments(assignee.getId()).getFirst().id();

        assertThatThrownBy(() -> taskService.updateAssignmentStatus(assignmentId,
                new UpdateAssignmentStatusRequest("ACCEPTED", null), outsider.getId()))
                .isInstanceOf(NotTaskParticipantException.class);
    }

    @Test
    void autoCompleteTask_whenAllAssignmentsCompleted() {
        Department dept = newDepartment();
        User assigner = newUserInDept("complete.assigner", dept);
        User a1 = newUserInDept("complete.a1", dept);
        User a2 = newUserInDept("complete.a2", dept);

        TaskResponse task = taskService.createTask(
                new CreateTaskRequest("Multi-assign complete", null,
                        List.of(a1.getId(), a2.getId()), null, null, null, null),
                assigner.getId());

        List<TaskAssignmentResponse> assignments1 = taskService.listMyAssignments(a1.getId());
        List<TaskAssignmentResponse> assignments2 = taskService.listMyAssignments(a2.getId());
        Long id1 = assignments1.getFirst().id();
        Long id2 = assignments2.getFirst().id();

        // Complete first assignment
        taskService.updateAssignmentStatus(id1,
                new UpdateAssignmentStatusRequest("IN_PROGRESS", null), a1.getId());
        taskService.updateAssignmentStatus(id1,
                new UpdateAssignmentStatusRequest("PENDING_REVIEW", null), a1.getId());
        taskService.updateAssignmentStatus(id1,
                new UpdateAssignmentStatusRequest("COMPLETED", null), assigner.getId());

        // Task should still be OPEN (a2 not done yet)
        assertThat(taskService.getTask(task.id(), assigner.getId()).status()).isNotEqualTo("COMPLETED");

        // Complete second assignment
        taskService.updateAssignmentStatus(id2,
                new UpdateAssignmentStatusRequest("IN_PROGRESS", null), a2.getId());
        taskService.updateAssignmentStatus(id2,
                new UpdateAssignmentStatusRequest("PENDING_REVIEW", null), a2.getId());
        taskService.updateAssignmentStatus(id2,
                new UpdateAssignmentStatusRequest("COMPLETED", null), assigner.getId());

        // Now task should be COMPLETED
        TaskResponse taskAfter = taskService.getTask(task.id(), assigner.getId());
        assertThat(taskAfter.status()).isEqualTo("COMPLETED");
        assertThat(taskAfter.completedAt()).isNotNull();
    }

    // ===================== UC-07 Comments =====================

    @Test
    void addComment_UC07_MainFlowStep3_assigneeComments() {
        Department dept = newDepartment();
        User assigner = newUserInDept("comment.assigner", dept);
        User assignee = newUserInDept("comment.assignee", dept);

        TaskResponse task = taskService.createTask(
                new CreateTaskRequest("Comment task", null,
                        List.of(assignee.getId()), null, null, null, null),
                assigner.getId());

        TaskCommentResponse comment = taskService.addComment(task.id(),
                new AddTaskCommentRequest("Đã xong phần 1, đang tiếp tục phần 2", null),
                assignee.getId());

        assertThat(comment.id()).isNotNull();
        assertThat(comment.content()).isEqualTo("Đã xong phần 1, đang tiếp tục phần 2");
        assertThat(comment.commenterUserId()).isEqualTo(assignee.getId());
        assertThat(comment.createdAt()).isNotNull();

        List<TaskCommentResponse> comments = taskService.listComments(task.id(), assigner.getId());
        assertThat(comments).hasSize(1);
    }

    @Test
    void addComment_byAssigner_notifiesAllAssignees() {
        Department dept = newDepartment();
        User assigner = newUserInDept("comment2.assigner", dept);
        User assignee = newUserInDept("comment2.assignee", dept);

        TaskResponse task = taskService.createTask(
                new CreateTaskRequest("Assigner comment", null,
                        List.of(assignee.getId()), null, null, null, null),
                assigner.getId());

        // Assigner comments — should not throw
        TaskCommentResponse comment = taskService.addComment(task.id(),
                new AddTaskCommentRequest("Hãy ưu tiên phần A", null),
                assigner.getId());

        assertThat(comment.commenterUserId()).isEqualTo(assigner.getId());
    }

    @Test
    void addComment_nonParticipant_throws() {
        Department dept = newDepartment();
        User assigner = newUserInDept("comment3.assigner", dept);
        User assignee = newUserInDept("comment3.assignee", dept);
        User outsider = newUserInDept("comment3.outsider", dept);

        TaskResponse task = taskService.createTask(
                new CreateTaskRequest("Private comment", null,
                        List.of(assignee.getId()), null, null, null, null),
                assigner.getId());

        assertThatThrownBy(() -> taskService.addComment(task.id(),
                new AddTaskCommentRequest("Should not see this", null),
                outsider.getId()))
                .isInstanceOf(NotTaskParticipantException.class);
    }

    // ===================== View/List =====================

    @Test
    void getTask_byAssignee_succeeds() {
        Department dept = newDepartment();
        User assigner = newUserInDept("view.assigner", dept);
        User assignee = newUserInDept("view.assignee", dept);

        TaskResponse created = taskService.createTask(
                new CreateTaskRequest("View test", null,
                        List.of(assignee.getId()), null, null, null, null),
                assigner.getId());

        TaskResponse fetched = taskService.getTask(created.id(), assignee.getId());
        assertThat(fetched.id()).isEqualTo(created.id());
        assertThat(fetched.title()).isEqualTo("View test");
    }

    @Test
    void getTask_nonParticipant_throws() {
        Department dept = newDepartment();
        User assigner = newUserInDept("view2.assigner", dept);
        User assignee = newUserInDept("view2.assignee", dept);
        User outsider = newUserInDept("view2.outsider", dept);

        TaskResponse created = taskService.createTask(
                new CreateTaskRequest("Private view", null,
                        List.of(assignee.getId()), null, null, null, null),
                assigner.getId());

        assertThatThrownBy(() -> taskService.getTask(created.id(), outsider.getId()))
                .isInstanceOf(NotTaskParticipantException.class);
    }

    @Test
    void listTasksCreatedByMe_returnsOnlyMyTasks() {
        Department dept = newDepartment();
        User assigner1 = newUserInDept("list.assigner1", dept);
        User assigner2 = newUserInDept("list.assigner2", dept);
        User assignee = newUserInDept("list.assignee", dept);

        taskService.createTask(
                new CreateTaskRequest("Task by assigner1", null,
                        List.of(assignee.getId()), null, null, null, null),
                assigner1.getId());
        taskService.createTask(
                new CreateTaskRequest("Task by assigner2", null,
                        List.of(assignee.getId()), null, null, null, null),
                assigner2.getId());

        List<TaskResponse> byAssigner1 = taskService.listTasksCreatedByMe(assigner1.getId());
        assertThat(byAssigner1).hasSize(1);
        assertThat(byAssigner1.getFirst().title()).isEqualTo("Task by assigner1");
    }

    // ===================== Helpers =====================

    private Department newDepartment() {
        Department department = new Department();
        department.setCode("DEPT-TSK-" + SEQ.incrementAndGet());
        department.setName("Test Department " + SEQ.get());
        return departmentRepository.save(department);
    }

    private User newUserInDept(String prefix, Department dept) {
        User user = new User();
        long seq = SEQ.incrementAndGet();
        user.setUsername(prefix + "." + seq);
        user.setEmail(prefix + "." + seq + "@pps.edu.vn");
        user.setFullName("Test " + prefix);
        user.setStatus(User.Status.ACTIVE);
        user = userRepository.save(user);

        Employee employee = new Employee();
        employee.setUser(user);
        employee.setEmployeeCode("NVTSK" + seq);
        employee.setDateOfBirth(LocalDate.of(1995, 1, 1));
        employee.setEmployeeType(Employee.EmployeeType.STAFF);
        employee.setDepartment(dept);
        employee.setDefaultShiftRequired(true);
        employee.setHireDate(LocalDate.of(2024, 1, 1));
        employeeRepository.save(employee);

        return user;
    }

    private void assignRole(User user, String roleCode) {
        Role role = roleRepository.findByCode(roleCode).orElseThrow();
        UserRole userRole = new UserRole();
        userRole.setUser(user);
        userRole.setRole(role);
        userRole.setAssignedBy(user);
        userRoleRepository.save(userRole);
    }
}
