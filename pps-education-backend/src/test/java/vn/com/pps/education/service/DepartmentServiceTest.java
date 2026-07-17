package vn.com.pps.education.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.domain.Employee;
import vn.com.pps.education.domain.Task;
import vn.com.pps.education.domain.User;
import vn.com.pps.education.dto.CreateDepartmentRequest;
import vn.com.pps.education.dto.DepartmentResponse;
import vn.com.pps.education.dto.UpdateDepartmentRequest;
import vn.com.pps.education.exception.DepartmentNotDeletableException;
import vn.com.pps.education.exception.DuplicateDepartmentCodeException;
import vn.com.pps.education.exception.ResourceNotFoundException;
import vn.com.pps.education.repository.DepartmentRepository;
import vn.com.pps.education.repository.EmployeeRepository;
import vn.com.pps.education.repository.TaskRepository;
import vn.com.pps.education.repository.UserRepository;
import vn.com.pps.education.support.AbstractIntegrationTest;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Bổ sung ngoài UC cụ thể — CRUD danh mục phòng ban (xem Javadoc
 * DepartmentService). Covers: Main Flow tạo/sửa/xem/liệt kê, mã trùng, xóa
 * bị chặn khi đang tham chiếu (nhân sự/công việc/phòng ban con), xóa thành
 * công khi không còn tham chiếu, not-found.
 */
@Transactional
class DepartmentServiceTest extends AbstractIntegrationTest {

    private static final AtomicLong SEQ = new AtomicLong();

    @Autowired private DepartmentService departmentService;
    @Autowired private DepartmentRepository departmentRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private EmployeeRepository employeeRepository;
    @Autowired private TaskRepository taskRepository;

    @Test
    void create_boSung_MainFlow_savesDepartment() {
        User head = newUser("head");

        DepartmentResponse response = departmentService.create(
                new CreateDepartmentRequest(uniqueCode(), "Phòng Đào tạo", head.getId(), null));

        assertThat(response.id()).isNotNull();
        assertThat(response.headUserId()).isEqualTo(head.getId());
        assertThat(response.headUserFullName()).isEqualTo(head.getFullName());
        assertThat(response.parentDepartmentId()).isNull();
    }

    @Test
    void create_boSung_withoutHeadOrParent_leavesThemNull() {
        DepartmentResponse response = departmentService.create(
                new CreateDepartmentRequest(uniqueCode(), "Phòng chưa có trưởng phòng", null, null));

        assertThat(response.headUserId()).isNull();
        assertThat(response.parentDepartmentId()).isNull();
    }

    @Test
    void create_boSung_duplicateCode_rejectsWithDuplicateDepartmentCodeException() {
        String code = uniqueCode();
        departmentService.create(new CreateDepartmentRequest(code, "Phòng 1", null, null));

        assertThatThrownBy(() -> departmentService.create(new CreateDepartmentRequest(code, "Phòng 2", null, null)))
                .isInstanceOf(DuplicateDepartmentCodeException.class);
    }

    @Test
    void update_boSung_MainFlow_changesNameHeadAndParent() {
        DepartmentResponse parent = departmentService.create(new CreateDepartmentRequest(uniqueCode(), "Phòng cha", null, null));
        DepartmentResponse child = departmentService.create(new CreateDepartmentRequest(uniqueCode(), "Phòng con", null, null));
        User newHead = newUser("new-head");

        DepartmentResponse updated = departmentService.update(child.id(),
                new UpdateDepartmentRequest("Phòng con đã đổi tên", newHead.getId(), parent.id()));

        assertThat(updated.name()).isEqualTo("Phòng con đã đổi tên");
        assertThat(updated.headUserId()).isEqualTo(newHead.getId());
        assertThat(updated.parentDepartmentId()).isEqualTo(parent.id());
        assertThat(updated.parentDepartmentName()).isEqualTo("Phòng cha");
    }

    @Test
    void update_boSung_settingParentToSelf_rejectsWithIllegalArgument() {
        DepartmentResponse department = departmentService.create(new CreateDepartmentRequest(uniqueCode(), "Phòng tự tham chiếu", null, null));

        assertThatThrownBy(() -> departmentService.update(department.id(),
                new UpdateDepartmentRequest("Phòng tự tham chiếu", null, department.id())))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void update_boSung_notFound_throwsResourceNotFoundException() {
        assertThatThrownBy(() -> departmentService.update(-1L, new UpdateDepartmentRequest("X", null, null)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getById_boSung_notFound_throwsResourceNotFoundException() {
        assertThatThrownBy(() -> departmentService.getById(-1L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void list_boSung_includesCreatedDepartment() {
        DepartmentResponse created = departmentService.create(new CreateDepartmentRequest(uniqueCode(), "Phòng list", null, null));

        List<DepartmentResponse> departments = departmentService.list();

        assertThat(departments).extracting(DepartmentResponse::id).contains(created.id());
    }

    @Test
    void delete_boSung_MainFlow_removesUnreferencedDepartment() {
        DepartmentResponse created = departmentService.create(new CreateDepartmentRequest(uniqueCode(), "Phòng xóa được", null, null));

        departmentService.delete(created.id());

        assertThat(departmentRepository.findById(created.id())).isEmpty();
    }

    @Test
    void delete_boSung_rejectsWhenHasEmployee() {
        DepartmentResponse department = departmentService.create(new CreateDepartmentRequest(uniqueCode(), "Phòng có nhân sự", null, null));
        User user = newUser("emp-user");
        Employee employee = new Employee();
        employee.setUser(user);
        employee.setEmployeeCode("EMP-" + SEQ.incrementAndGet());
        employee.setDateOfBirth(LocalDate.of(1995, 1, 1));
        employee.setEmployeeType(Employee.EmployeeType.STAFF);
        employee.setHireDate(LocalDate.now());
        employee.setDepartment(departmentRepository.findById(department.id()).orElseThrow());
        employeeRepository.save(employee);

        assertThatThrownBy(() -> departmentService.delete(department.id()))
                .isInstanceOf(DepartmentNotDeletableException.class);
    }

    @Test
    void delete_boSung_rejectsWhenHasTask() {
        DepartmentResponse department = departmentService.create(new CreateDepartmentRequest(uniqueCode(), "Phòng có công việc", null, null));
        User creator = newUser("task-creator");
        Task task = new Task();
        task.setTaskCode("TASK-" + SEQ.incrementAndGet());
        task.setTitle("Việc test");
        task.setCreatedBy(creator);
        task.setDepartment(departmentRepository.findById(department.id()).orElseThrow());
        taskRepository.save(task);

        assertThatThrownBy(() -> departmentService.delete(department.id()))
                .isInstanceOf(DepartmentNotDeletableException.class);
    }

    @Test
    void delete_boSung_rejectsWhenHasChildDepartment() {
        DepartmentResponse parent = departmentService.create(new CreateDepartmentRequest(uniqueCode(), "Phòng cha có con", null, null));
        departmentService.create(new CreateDepartmentRequest(uniqueCode(), "Phòng con", null, parent.id()));

        assertThatThrownBy(() -> departmentService.delete(parent.id()))
                .isInstanceOf(DepartmentNotDeletableException.class);
    }

    // ===================== Helpers =====================

    private String uniqueCode() {
        return "DEPT-TEST-" + SEQ.incrementAndGet();
    }

    private User newUser(String prefix) {
        User user = new User();
        long seq = SEQ.incrementAndGet();
        user.setUsername(prefix + "." + seq);
        user.setEmail(prefix + "." + seq + "@pps.edu.vn");
        user.setFullName("Test " + prefix);
        user.setStatus(User.Status.ACTIVE);
        return userRepository.save(user);
    }
}
