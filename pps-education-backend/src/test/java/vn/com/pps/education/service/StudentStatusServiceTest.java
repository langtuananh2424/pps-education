package vn.com.pps.education.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.domain.Role;
import vn.com.pps.education.domain.User;
import vn.com.pps.education.domain.UserRole;
import vn.com.pps.education.dto.CreateStudentRequest;
import vn.com.pps.education.dto.StudentResponse;
import vn.com.pps.education.dto.UpdateStudentStatusRequest;
import vn.com.pps.education.exception.InvalidStudentStatusTransitionException;
import vn.com.pps.education.exception.NotAuthorizedForStudentStatusException;
import vn.com.pps.education.repository.RoleRepository;
import vn.com.pps.education.repository.UserRepository;
import vn.com.pps.education.repository.UserRoleRepository;
import vn.com.pps.education.support.AbstractIntegrationTest;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * UC-14: Cập nhật trạng thái học tập — Main Flow (bước 1-5), A1 (chuyển
 * trạng thái không hợp lệ). Xem docs/uc/phan-he-05-hoc-sinh.md.
 */
@Transactional
class StudentStatusServiceTest extends AbstractIntegrationTest {

    @Autowired
    private StudentService studentService;

    @Autowired
    private StudentStatusService studentStatusService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserRoleRepository userRoleRepository;

    private User siteManager;

    @BeforeEach
    void setUp() {
        siteManager = newUser("site.manager");
        assignRole(siteManager, "SITE_MANAGER");
    }

    @Test
    void updateStatus_UC14_MainFlow_transitionsStatusAndWritesHistory() {
        StudentResponse student = createStudent();

        var history = studentStatusService.updateStatus(student.id(),
                new UpdateStudentStatusRequest("SUSPENDED", "Vi phạm nội quy", LocalDate.now()),
                siteManager.getId());

        assertThat(history.oldStatus()).isEqualTo("ACTIVE");
        assertThat(history.newStatus()).isEqualTo("SUSPENDED");
        assertThat(studentService.getById(student.id()).status()).isEqualTo("SUSPENDED");
        assertThat(studentStatusService.listStatusHistory(student.id())).containsExactly(history);
    }

    @Test
    void updateStatus_UC14_MainFlow_settingGraduatedRecordsGraduationDate() {
        StudentResponse student = createStudent();
        LocalDate graduationDate = LocalDate.now();

        studentStatusService.updateStatus(student.id(),
                new UpdateStudentStatusRequest("GRADUATED", "Hoàn thành khóa học", graduationDate),
                siteManager.getId());

        assertThat(studentService.getById(student.id()).graduationDate()).isEqualTo(graduationDate);
    }

    @Test
    void updateStatus_UC14_A1_rejectsGraduatedToActiveWithoutSpecialConfirmation() {
        StudentResponse student = createStudent();
        studentStatusService.updateStatus(student.id(),
                new UpdateStudentStatusRequest("GRADUATED", "Hoàn thành khóa học", LocalDate.now()),
                siteManager.getId());

        assertThatThrownBy(() -> studentStatusService.updateStatus(student.id(),
                new UpdateStudentStatusRequest("ACTIVE", "Học lại", LocalDate.now()),
                siteManager.getId()))
                .isInstanceOf(InvalidStudentStatusTransitionException.class);
    }

    @Test
    void updateStatus_UC14_A1_rejectsNoOpTransition() {
        StudentResponse student = createStudent();

        assertThatThrownBy(() -> studentStatusService.updateStatus(student.id(),
                new UpdateStudentStatusRequest("ACTIVE", "Không đổi gì", LocalDate.now()),
                siteManager.getId()))
                .isInstanceOf(InvalidStudentStatusTransitionException.class);
    }

    @Test
    void updateStatus_rejectsActorWithoutAuthorizedRole() {
        StudentResponse student = createStudent();
        User unauthorized = newUser("no.role.user");

        assertThatThrownBy(() -> studentStatusService.updateStatus(student.id(),
                new UpdateStudentStatusRequest("SUSPENDED", "x", LocalDate.now()),
                unauthorized.getId()))
                .isInstanceOf(NotAuthorizedForStudentStatusException.class);
    }

    private StudentResponse createStudent() {
        User studentUser = newUser("student.status");
        return studentService.create(
                new CreateStudentRequest(studentUser.getId(), LocalDate.of(2012, 5, 1), "MALE", null, null,
                        null, null, LocalDate.of(2026, 1, 1), null),
                siteManager.getId());
    }

    private void assignRole(User user, String roleCode) {
        Role role = roleRepository.findByCode(roleCode).orElseThrow();
        UserRole userRole = new UserRole();
        userRole.setUser(user);
        userRole.setRole(role);
        userRole.setAssignedBy(user);
        userRoleRepository.save(userRole);
    }

    private User newUser(String prefix) {
        User user = new User();
        user.setUsername(prefix + "." + System.nanoTime());
        user.setEmail(prefix + "." + System.nanoTime() + "@pps.edu.vn");
        user.setFullName("Test " + prefix);
        user.setStatus(User.Status.ACTIVE);
        return userRepository.save(user);
    }
}
