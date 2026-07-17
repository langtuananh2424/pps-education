package vn.com.pps.education.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.domain.Employee;
import vn.com.pps.education.domain.Role;
import vn.com.pps.education.domain.User;
import vn.com.pps.education.dto.CreatePositionRequest;
import vn.com.pps.education.dto.PositionDefaultRolesResponse;
import vn.com.pps.education.dto.PositionResponse;
import vn.com.pps.education.dto.UpdatePositionDefaultRolesRequest;
import vn.com.pps.education.dto.UpdatePositionRequest;
import vn.com.pps.education.exception.DuplicatePositionCodeException;
import vn.com.pps.education.exception.PositionNotDeletableException;
import vn.com.pps.education.exception.ResourceNotFoundException;
import vn.com.pps.education.repository.EmployeeRepository;
import vn.com.pps.education.repository.PositionRepository;
import vn.com.pps.education.repository.RoleRepository;
import vn.com.pps.education.repository.UserRepository;
import vn.com.pps.education.support.AbstractIntegrationTest;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * FR-HRM-06/UC-52: danh mục Chức vụ + ánh xạ role mặc định (xem Javadoc
 * PositionService). Covers: Main Flow tạo/sửa/xem/liệt kê, mã trùng, cấu
 * hình role mặc định (add/remove diff), xóa bị chặn khi đang có nhân sự
 * (A1), xóa thành công khi không còn tham chiếu, not-found.
 */
@Transactional
class PositionServiceTest extends AbstractIntegrationTest {

    private static final AtomicLong SEQ = new AtomicLong();

    @Autowired private PositionService positionService;
    @Autowired private PositionRepository positionRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private EmployeeRepository employeeRepository;
    @Autowired private UserRepository userRepository;

    @Test
    void create_UC52_MainFlow_savesPosition() {
        PositionResponse response = positionService.create(new CreatePositionRequest(uniqueCode(), "Giáo viên"));

        assertThat(response.id()).isNotNull();
        assertThat(response.name()).isEqualTo("Giáo viên");
    }

    @Test
    void create_UC52_duplicateCode_rejectsWithDuplicatePositionCodeException() {
        String code = uniqueCode();
        positionService.create(new CreatePositionRequest(code, "Chức vụ 1"));

        assertThatThrownBy(() -> positionService.create(new CreatePositionRequest(code, "Chức vụ 2")))
                .isInstanceOf(DuplicatePositionCodeException.class);
    }

    @Test
    void update_UC52_MainFlow_changesName() {
        PositionResponse created = positionService.create(new CreatePositionRequest(uniqueCode(), "Tên cũ"));

        PositionResponse updated = positionService.update(created.id(), new UpdatePositionRequest("Tên mới"));

        assertThat(updated.name()).isEqualTo("Tên mới");
    }

    @Test
    void update_UC52_notFound_throwsResourceNotFoundException() {
        assertThatThrownBy(() -> positionService.update(-1L, new UpdatePositionRequest("X")))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getById_UC52_notFound_throwsResourceNotFoundException() {
        assertThatThrownBy(() -> positionService.getById(-1L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void list_UC52_includesCreatedPosition() {
        PositionResponse created = positionService.create(new CreatePositionRequest(uniqueCode(), "Chức vụ list"));

        List<PositionResponse> positions = positionService.list();

        assertThat(positions).extracting(PositionResponse::id).contains(created.id());
    }

    @Test
    void updateDefaultRoles_UC52_MainFlow_setsMultipleDefaultRoles() {
        PositionResponse position = positionService.create(new CreatePositionRequest(uniqueCode(), "Trưởng phòng đào tạo"));
        Role teacher = roleRepository.findByCode("TEACHER").orElseThrow();
        Role headAcademic = roleRepository.findByCode("HEAD_ACADEMIC").orElseThrow();

        positionService.updateDefaultRoles(position.id(),
                new UpdatePositionDefaultRolesRequest(Set.of(teacher.getId(), headAcademic.getId())));

        PositionDefaultRolesResponse result = positionService.getDefaultRoles(position.id());
        assertThat(result.defaultRoles()).extracting(r -> r.code()).containsExactlyInAnyOrder("TEACHER", "HEAD_ACADEMIC");
    }

    @Test
    void updateDefaultRoles_UC52_MainFlow_replacesPreviousSetOnSecondCall() {
        PositionResponse position = positionService.create(new CreatePositionRequest(uniqueCode(), "Giáo viên"));
        Role teacher = roleRepository.findByCode("TEACHER").orElseThrow();
        Role staff = roleRepository.findByCode("STAFF").orElseThrow();
        positionService.updateDefaultRoles(position.id(), new UpdatePositionDefaultRolesRequest(Set.of(teacher.getId())));

        positionService.updateDefaultRoles(position.id(), new UpdatePositionDefaultRolesRequest(Set.of(staff.getId())));

        PositionDefaultRolesResponse result = positionService.getDefaultRoles(position.id());
        assertThat(result.defaultRoles()).extracting(r -> r.code()).containsExactly("STAFF");
    }

    @Test
    void delete_UC52_MainFlow_removesUnreferencedPosition() {
        PositionResponse created = positionService.create(new CreatePositionRequest(uniqueCode(), "Chức vụ xóa được"));

        positionService.delete(created.id());

        assertThat(positionRepository.findById(created.id())).isEmpty();
    }

    @Test
    void delete_UC52_A1_rejectsWhenAssignedToEmployee() {
        PositionResponse position = positionService.create(new CreatePositionRequest(uniqueCode(), "Chức vụ có nhân sự"));
        User user = newUser("pos-emp-user");
        Employee employee = new Employee();
        employee.setUser(user);
        employee.setEmployeeCode("EMP-POS-" + SEQ.incrementAndGet());
        employee.setDateOfBirth(LocalDate.of(1995, 1, 1));
        employee.setEmployeeType(Employee.EmployeeType.STAFF);
        employee.setHireDate(LocalDate.now());
        employee.setPosition(positionRepository.findById(position.id()).orElseThrow());
        employeeRepository.save(employee);

        assertThatThrownBy(() -> positionService.delete(position.id()))
                .isInstanceOf(PositionNotDeletableException.class);
    }

    // ===================== Helpers =====================

    private String uniqueCode() {
        return "POS-TEST-" + SEQ.incrementAndGet();
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
