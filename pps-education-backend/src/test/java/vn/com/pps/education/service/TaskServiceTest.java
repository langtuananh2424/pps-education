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
import vn.com.pps.education.exception.NotAuthorizedForTaskOverviewException;
import vn.com.pps.education.exception.NotTaskCreatorException;
import vn.com.pps.education.exception.NotTaskParticipantException;
import vn.com.pps.education.exception.ResourceNotFoundException;
import vn.com.pps.education.exception.TaskAssigneeAlreadyAssignedException;
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
    @Autowired private vn.com.pps.education.repository.PermissionRepository permissionRepository;
    @Autowired private vn.com.pps.education.repository.UserPermissionOverrideRepository userPermissionOverrideRepository;

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
    void createTask_UC06_executive_canAssignCrossDepartment() {
        // Ban giám đốc (EXECUTIVE) = company-wide, giao được cho bất kỳ ai (kể cả khác phòng / trưởng phòng).
        Department deptA = newDepartment();
        Department deptB = newDepartment();
        User executive = newUserInDept("exec", deptA);
        assignRole(executive, "EXECUTIVE");
        User assignee = newUserInDept("assignee.cross", deptB);

        TaskResponse response = taskService.createTask(
                new CreateTaskRequest("Cross-dept allowed", null,
                        List.of(assignee.getId()), null, null, null, null),
                executive.getId());

        assertThat(response.id()).isNotNull();
    }

    @Test
    void createTask_UC06_taskManage_canAssignCrossDepartment() {
        // Quyền task.manage = company-wide (ban quản lý).
        Department deptA = newDepartment();
        Department deptB = newDepartment();
        User manager = newUserInDept("mgr", deptA);
        grantPermission(manager, "task.manage");
        User assignee = newUserInDept("assignee.mgr", deptB);

        TaskResponse response = taskService.createTask(
                new CreateTaskRequest("Manager cross-dept", null,
                        List.of(assignee.getId()), null, null, null, null),
                manager.getId());

        assertThat(response.id()).isNotNull();
    }

    @Test
    void createTask_UC06_plainEmployeeNotHead_cannotAssign_throws() {
        // Nhân viên thường (không là trưởng phòng, không company-wide) không giao việc được.
        Department dept = newDepartment();
        newUserInDept("plain.head", dept); // người đầu tiên = trưởng phòng
        User plainEmployee = newUserInDept("plain.emp", dept);
        User assignee = newUserInDept("plain.assignee", dept);

        assertThatThrownBy(() -> taskService.createTask(
                new CreateTaskRequest("Plain cannot assign", null,
                        List.of(assignee.getId()), null, null, null, null),
                plainEmployee.getId()))
                .isInstanceOf(AssigneeOutsideDepartmentException.class);
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

    // ===================== UC-07 A3: từ chối + giao lại =====================

    @Test
    void updateAssignmentStatus_UC07_A3_singleAssigneeDeclines_taskNotStuckCompleted() {
        Department dept = newDepartment();
        User assigner = newUserInDept("a3.decline.assigner", dept);
        User assignee = newUserInDept("a3.decline.assignee", dept);

        TaskResponse task = taskService.createTask(
                new CreateTaskRequest("A3 single decline", null,
                        List.of(assignee.getId()), null, null, null, null),
                assigner.getId());
        Long assignmentId = taskService.listMyAssignments(assignee.getId()).getFirst().id();

        taskService.updateAssignmentStatus(assignmentId,
                new UpdateAssignmentStatusRequest("DECLINED", "Bận việc khác"), assignee.getId());

        // Lone declined assignment must NOT auto-complete the task; stays open awaiting reassign
        TaskResponse after = taskService.getTask(task.id(), assigner.getId());
        assertThat(after.status()).isEqualTo("OPEN");
        assertThat(after.completedAt()).isNull();
    }

    @Test
    void recompute_UC07_A3_multiAssignee_declinedIgnored_completesOnRemaining() {
        Department dept = newDepartment();
        User assigner = newUserInDept("a3.multi.assigner", dept);
        User a1 = newUserInDept("a3.multi.a1", dept);
        User a2 = newUserInDept("a3.multi.a2", dept);

        TaskResponse task = taskService.createTask(
                new CreateTaskRequest("A3 multi decline", null,
                        List.of(a1.getId(), a2.getId()), null, null, null, null),
                assigner.getId());
        Long id1 = taskService.listMyAssignments(a1.getId()).getFirst().id();
        Long id2 = taskService.listMyAssignments(a2.getId()).getFirst().id();

        // a1 completes
        taskService.updateAssignmentStatus(id1, new UpdateAssignmentStatusRequest("IN_PROGRESS", null), a1.getId());
        taskService.updateAssignmentStatus(id1, new UpdateAssignmentStatusRequest("PENDING_REVIEW", null), a1.getId());
        taskService.updateAssignmentStatus(id1, new UpdateAssignmentStatusRequest("COMPLETED", null), assigner.getId());

        // Still open: a2 still PENDING (active, not completed)
        assertThat(taskService.getTask(task.id(), assigner.getId()).status()).isNotEqualTo("COMPLETED");

        // a2 declines → only active assignment (a1) is COMPLETED → task COMPLETED
        taskService.updateAssignmentStatus(id2, new UpdateAssignmentStatusRequest("DECLINED", "Không thể nhận"), a2.getId());

        assertThat(taskService.getTask(task.id(), assigner.getId()).status()).isEqualTo("COMPLETED");
    }

    @Test
    void reassign_UC07_A3_afterDecline_createsNewAssignmentAndCanComplete() {
        Department dept = newDepartment();
        User assigner = newUserInDept("a3.re.assigner", dept);
        User assignee = newUserInDept("a3.re.assignee", dept);
        User newAssignee = newUserInDept("a3.re.new", dept);

        TaskResponse task = taskService.createTask(
                new CreateTaskRequest("A3 reassign", null,
                        List.of(assignee.getId()), null, null, null, null),
                assigner.getId());
        Long declinedId = taskService.listMyAssignments(assignee.getId()).getFirst().id();
        taskService.updateAssignmentStatus(declinedId,
                new UpdateAssignmentStatusRequest("DECLINED", "Không nhận"), assignee.getId());

        TaskAssignmentResponse fresh = taskService.reassign(task.id(),
                new ReassignTaskRequest(declinedId, newAssignee.getId(), "Giao lại cho bạn"),
                assigner.getId());

        assertThat(fresh.id()).isNotNull();
        assertThat(fresh.assigneeUserId()).isEqualTo(newAssignee.getId());
        assertThat(fresh.assignmentStatus()).isEqualTo("PENDING");

        // DECLINED record preserved as history — task now has 2 assignments
        List<TaskAssignmentResponse> all = taskService.listAssignments(task.id(), assigner.getId());
        assertThat(all).hasSize(2);

        // New assignee completes the reassigned work → task COMPLETED (declined ignored)
        taskService.updateAssignmentStatus(fresh.id(),
                new UpdateAssignmentStatusRequest("IN_PROGRESS", null), newAssignee.getId());
        taskService.updateAssignmentStatus(fresh.id(),
                new UpdateAssignmentStatusRequest("PENDING_REVIEW", null), newAssignee.getId());
        taskService.updateAssignmentStatus(fresh.id(),
                new UpdateAssignmentStatusRequest("COMPLETED", null), assigner.getId());

        assertThat(taskService.getTask(task.id(), assigner.getId()).status()).isEqualTo("COMPLETED");
    }

    @Test
    void reassign_UC07_A3_byNonCreator_throws() {
        Department dept = newDepartment();
        User assigner = newUserInDept("a3.nc.assigner", dept);
        User assignee = newUserInDept("a3.nc.assignee", dept);
        User other = newUserInDept("a3.nc.other", dept);

        TaskResponse task = taskService.createTask(
                new CreateTaskRequest("A3 non-creator", null,
                        List.of(assignee.getId()), null, null, null, null),
                assigner.getId());
        Long declinedId = taskService.listMyAssignments(assignee.getId()).getFirst().id();
        taskService.updateAssignmentStatus(declinedId,
                new UpdateAssignmentStatusRequest("DECLINED", "x"), assignee.getId());

        assertThatThrownBy(() -> taskService.reassign(task.id(),
                new ReassignTaskRequest(declinedId, other.getId(), null), assignee.getId()))
                .isInstanceOf(NotTaskCreatorException.class);
    }

    @Test
    void reassign_UC07_A3_nonDeclinedAssignment_throws() {
        Department dept = newDepartment();
        User assigner = newUserInDept("a3.nd.assigner", dept);
        User assignee = newUserInDept("a3.nd.assignee", dept);
        User other = newUserInDept("a3.nd.other", dept);

        TaskResponse task = taskService.createTask(
                new CreateTaskRequest("A3 non-declined", null,
                        List.of(assignee.getId()), null, null, null, null),
                assigner.getId());
        Long pendingId = taskService.listMyAssignments(assignee.getId()).getFirst().id();

        // Assignment is still PENDING (not DECLINED) → cannot reassign
        assertThatThrownBy(() -> taskService.reassign(task.id(),
                new ReassignTaskRequest(pendingId, other.getId(), null), assigner.getId()))
                .isInstanceOf(InvalidTaskStatusTransitionException.class);
    }

    @Test
    void reassign_UC07_A3_toExistingAssignee_throws() {
        Department dept = newDepartment();
        User assigner = newUserInDept("a3.ex.assigner", dept);
        User a1 = newUserInDept("a3.ex.a1", dept);
        User a2 = newUserInDept("a3.ex.a2", dept);

        TaskResponse task = taskService.createTask(
                new CreateTaskRequest("A3 existing assignee", null,
                        List.of(a1.getId(), a2.getId()), null, null, null, null),
                assigner.getId());
        Long id1 = taskService.listMyAssignments(a1.getId()).getFirst().id();
        taskService.updateAssignmentStatus(id1,
                new UpdateAssignmentStatusRequest("DECLINED", "x"), a1.getId());

        // a2 already has an assignment on this task → conflict
        assertThatThrownBy(() -> taskService.reassign(task.id(),
                new ReassignTaskRequest(id1, a2.getId(), null), assigner.getId()))
                .isInstanceOf(TaskAssigneeAlreadyAssignedException.class);
    }

    @Test
    void reassign_UC07_A3_outsideDepartment_throws() {
        Department deptA = newDepartment();
        Department deptB = newDepartment();
        User assigner = newUserInDept("a3.od.assigner", deptA);
        User assignee = newUserInDept("a3.od.assignee", deptA);
        User outsider = newUserInDept("a3.od.outsider", deptB);

        TaskResponse task = taskService.createTask(
                new CreateTaskRequest("A3 outside dept", null,
                        List.of(assignee.getId()), null, null, null, null),
                assigner.getId());
        Long declinedId = taskService.listMyAssignments(assignee.getId()).getFirst().id();
        taskService.updateAssignmentStatus(declinedId,
                new UpdateAssignmentStatusRequest("DECLINED", "x"), assignee.getId());

        assertThatThrownBy(() -> taskService.reassign(task.id(),
                new ReassignTaskRequest(declinedId, outsider.getId(), null), assigner.getId()))
                .isInstanceOf(AssigneeOutsideDepartmentException.class);
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
    void listAssignments_UC07_A2_byCreator_returnsAllAssigneesWithAssignmentIds() {
        Department dept = newDepartment();
        User assigner = newUserInDept("la.assigner", dept);
        User assignee1 = newUserInDept("la.assignee1", dept);
        User assignee2 = newUserInDept("la.assignee2", dept);

        TaskResponse created = taskService.createTask(
                new CreateTaskRequest("Multi-assignee review", null,
                        List.of(assignee1.getId(), assignee2.getId()), null, null, null, null),
                assigner.getId());

        List<TaskAssignmentResponse> assignments = taskService.listAssignments(created.id(), assigner.getId());

        assertThat(assignments).hasSize(2);
        assertThat(assignments).extracting(TaskAssignmentResponse::assigneeUserId)
                .containsExactlyInAnyOrder(assignee1.getId(), assignee2.getId());
        assertThat(assignments).allSatisfy(a -> assertThat(a.id()).isNotNull());
    }

    @Test
    void listAssignments_byAssignee_throws() {
        Department dept = newDepartment();
        User assigner = newUserInDept("la2.assigner", dept);
        User assignee = newUserInDept("la2.assignee", dept);

        TaskResponse created = taskService.createTask(
                new CreateTaskRequest("Assignee cannot list", null,
                        List.of(assignee.getId()), null, null, null, null),
                assigner.getId());

        assertThatThrownBy(() -> taskService.listAssignments(created.id(), assignee.getId()))
                .isInstanceOf(NotTaskCreatorException.class);
    }

    @Test
    void listAssignments_byOutsider_throws() {
        Department dept = newDepartment();
        User assigner = newUserInDept("la3.assigner", dept);
        User assignee = newUserInDept("la3.assignee", dept);
        User outsider = newUserInDept("la3.outsider", dept);

        TaskResponse created = taskService.createTask(
                new CreateTaskRequest("Outsider cannot list", null,
                        List.of(assignee.getId()), null, null, null, null),
                assigner.getId());

        assertThatThrownBy(() -> taskService.listAssignments(created.id(), outsider.getId()))
                .isInstanceOf(NotTaskCreatorException.class);
    }

    @Test
    void listTasksCreatedByMe_returnsOnlyMyTasks() {
        Department dept = newDepartment();
        User assigner1 = newUserInDept("list.assigner1", dept);
        User assigner2 = newUserInDept("list.assigner2", dept);
        User assignee = newUserInDept("list.assignee", dept);
        // Cả 2 người giao là EXECUTIVE (company-wide) để giao cùng 1 người nhận (assigner2 không là trưởng phòng).
        assignRole(assigner1, "EXECUTIVE");
        assignRole(assigner2, "EXECUTIVE");

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

    // ===================== UC-06/07: Tổng quan công việc (GET /api/tasks/overview) =====================

    @Test
    void listOverview_companyPerm_returnsAllTasksAcrossDepartments() {
        Department dept1 = newDepartment();
        User head1 = newUserInDept("ov.co.head1", dept1);
        User assignee1 = newUserInDept("ov.co.a1", dept1);
        TaskResponse task1 = taskService.createTask(
                new CreateTaskRequest("Overview dept1", null, List.of(assignee1.getId()), null, null, null, null),
                head1.getId());

        Department dept2 = newDepartment();
        User head2 = newUserInDept("ov.co.head2", dept2);
        User assignee2 = newUserInDept("ov.co.a2", dept2);
        TaskResponse task2 = taskService.createTask(
                new CreateTaskRequest("Overview dept2", null, List.of(assignee2.getId()), null, null, null, null),
                head2.getId());

        User viewer = newUserInDept("ov.co.viewer", newDepartment());
        grantPermission(viewer, "task.overview.company");

        List<TaskResponse> overview = taskService.listOverview(viewer.getId());

        assertThat(overview).extracting(TaskResponse::id).contains(task1.id(), task2.id());
    }

    @Test
    void listOverview_deptHead_returnsOwnDepartmentTasksOnly() {
        Department dept1 = newDepartment();
        User head1 = newUserInDept("ov.dh.head1", dept1);
        User assignee1 = newUserInDept("ov.dh.a1", dept1);
        TaskResponse task1 = taskService.createTask(
                new CreateTaskRequest("DeptHead dept1", null, List.of(assignee1.getId()), null, null, null, null),
                head1.getId());

        Department dept2 = newDepartment();
        User head2 = newUserInDept("ov.dh.head2", dept2);
        User assignee2 = newUserInDept("ov.dh.a2", dept2);
        TaskResponse task2 = taskService.createTask(
                new CreateTaskRequest("DeptHead dept2", null, List.of(assignee2.getId()), null, null, null, null),
                head2.getId());

        // head1 chỉ làm trưởng dept1 -> thấy task1, KHÔNG thấy task2
        List<TaskResponse> overview = taskService.listOverview(head1.getId());

        assertThat(overview).extracting(TaskResponse::id).contains(task1.id());
        assertThat(overview).extracting(TaskResponse::id).doesNotContain(task2.id());
    }

    @Test
    void listOverview_plainEmployee_throws403() {
        Department dept = newDepartment();
        newUserInDept("ov.plain.head", dept); // người đầu tiên = trưởng phòng
        User plainEmployee = newUserInDept("ov.plain.emp", dept);

        assertThatThrownBy(() -> taskService.listOverview(plainEmployee.getId()))
                .isInstanceOf(NotAuthorizedForTaskOverviewException.class);
    }

    // ===================== UC-06/07: Hủy công việc (CANCELLED thay vì xóa) =====================

    @Test
    void cancelTask_UC06_byCreator_setsCancelled() {
        Department dept = newDepartment();
        User assigner = newUserInDept("cancel.assigner", dept);
        User assignee = newUserInDept("cancel.assignee", dept);
        TaskResponse task = taskService.createTask(
                new CreateTaskRequest("Cancel me", null, List.of(assignee.getId()), null, null, null, null),
                assigner.getId());

        TaskResponse cancelled = taskService.cancelTask(task.id(), new CancelTaskRequest("Không cần nữa"), assigner.getId());

        assertThat(cancelled.status()).isEqualTo("CANCELLED");
        assertThat(taskService.getTask(task.id(), assigner.getId()).status()).isEqualTo("CANCELLED");
    }

    @Test
    void cancelTask_UC06_blocksAssignmentUpdateAfterCancel() {
        Department dept = newDepartment();
        User assigner = newUserInDept("cancel2.assigner", dept);
        User assignee = newUserInDept("cancel2.assignee", dept);
        TaskResponse task = taskService.createTask(
                new CreateTaskRequest("Cancel block", null, List.of(assignee.getId()), null, null, null, null),
                assigner.getId());
        Long assignmentId = taskService.listMyAssignments(assignee.getId()).getFirst().id();
        taskService.cancelTask(task.id(), null, assigner.getId());

        assertThatThrownBy(() -> taskService.updateAssignmentStatus(assignmentId,
                new UpdateAssignmentStatusRequest("ACCEPTED", null), assignee.getId()))
                .isInstanceOf(InvalidTaskStatusTransitionException.class);
    }

    @Test
    void cancelTask_UC06_byNonCreatorWithoutManage_throws() {
        Department dept = newDepartment();
        User assigner = newUserInDept("cancel3.assigner", dept);
        User assignee = newUserInDept("cancel3.assignee", dept);
        TaskResponse task = taskService.createTask(
                new CreateTaskRequest("Cancel deny", null, List.of(assignee.getId()), null, null, null, null),
                assigner.getId());

        assertThatThrownBy(() -> taskService.cancelTask(task.id(), null, assignee.getId()))
                .isInstanceOf(NotTaskCreatorException.class);
    }

    @Test
    void cancelTask_UC06_alreadyCompleted_throws() {
        Department dept = newDepartment();
        User assigner = newUserInDept("cancel4.assigner", dept);
        User assignee = newUserInDept("cancel4.assignee", dept);
        TaskResponse task = taskService.createTask(
                new CreateTaskRequest("Cancel completed", null, List.of(assignee.getId()), null, null, null, null),
                assigner.getId());
        Long assignmentId = taskService.listMyAssignments(assignee.getId()).getFirst().id();
        taskService.updateAssignmentStatus(assignmentId, new UpdateAssignmentStatusRequest("IN_PROGRESS", null), assignee.getId());
        taskService.updateAssignmentStatus(assignmentId, new UpdateAssignmentStatusRequest("PENDING_REVIEW", null), assignee.getId());
        taskService.updateAssignmentStatus(assignmentId, new UpdateAssignmentStatusRequest("COMPLETED", null), assigner.getId());

        assertThatThrownBy(() -> taskService.cancelTask(task.id(), null, assigner.getId()))
                .isInstanceOf(InvalidTaskStatusTransitionException.class);
    }

    @Test
    void cancelTask_UC06_byManagerWithTaskManage_succeeds() {
        Department dept = newDepartment();
        User assigner = newUserInDept("cancel5.assigner", dept);
        User assignee = newUserInDept("cancel5.assignee", dept);
        TaskResponse task = taskService.createTask(
                new CreateTaskRequest("Cancel by manager", null, List.of(assignee.getId()), null, null, null, null),
                assigner.getId());
        User manager = newUserInDept("cancel5.manager", newDepartment());
        grantPermission(manager, "task.manage");

        TaskResponse cancelled = taskService.cancelTask(task.id(), new CancelTaskRequest("admin hủy"), manager.getId());

        assertThat(cancelled.status()).isEqualTo("CANCELLED");
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

        // Người ĐẦU TIÊN tạo trong 1 phòng = trưởng phòng (head_user_id) — trong mọi test đây
        // chính là người giao việc, khớp mô hình phạm vi mới (trưởng phòng giao cho nhân sự phòng mình).
        if (dept.getHeadUser() == null) {
            dept.setHeadUser(user);
            departmentRepository.save(dept);
        }
        return user;
    }

    /** Cấp 1 quyền lẻ cho user qua override GRANT (VD task.manage) — dùng để test luồng company-wide. */
    private void grantPermission(User user, String permissionCode) {
        vn.com.pps.education.domain.Permission permission = permissionRepository.findByCode(permissionCode).orElseThrow();
        vn.com.pps.education.domain.UserPermissionOverride override = new vn.com.pps.education.domain.UserPermissionOverride();
        override.setUser(user);
        override.setPermission(permission);
        override.setOverrideType(vn.com.pps.education.domain.UserPermissionOverride.OverrideType.GRANT);
        override.setReason("test");
        override.setGrantedBy(user);
        userPermissionOverrideRepository.save(override);
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
