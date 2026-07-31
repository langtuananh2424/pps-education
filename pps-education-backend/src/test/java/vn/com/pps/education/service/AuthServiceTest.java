package vn.com.pps.education.service;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.domain.LoginAttempt;
import vn.com.pps.education.domain.Role;
import vn.com.pps.education.domain.Student;
import vn.com.pps.education.domain.User;
import vn.com.pps.education.domain.UserRole;
import vn.com.pps.education.dto.CurrentUserResponse;
import vn.com.pps.education.dto.LoginRequest;
import vn.com.pps.education.dto.LoginResponse;
import vn.com.pps.education.exception.AccountInactiveException;
import vn.com.pps.education.exception.AccountLockedException;
import vn.com.pps.education.exception.InvalidCredentialsException;
import vn.com.pps.education.repository.LoginAttemptRepository;
import vn.com.pps.education.repository.RoleRepository;
import vn.com.pps.education.repository.StudentRepository;
import vn.com.pps.education.repository.UserRepository;
import vn.com.pps.education.repository.UserRoleRepository;
import vn.com.pps.education.security.JwtService;
import vn.com.pps.education.support.AbstractIntegrationTest;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * UC-01: Đăng nhập hệ thống — luồng Tài khoản/Mật khẩu (Main Flow + A1/A2/A3).
 * Xem docs/uc/phan-he-01-dang-nhap.md. Luồng Google (A4) xem AuthServiceGoogleLoginTest.
 */
@Transactional
class AuthServiceTest extends AbstractIntegrationTest {

    private static final String RAW_PASSWORD = "Password@123";

    @Autowired
    private AuthService authService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private LoginAttemptRepository loginAttemptRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserRoleRepository userRoleRepository;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    private User activeUser;

    private List<LoginAttempt> attemptsFor(User user) {
        return loginAttemptRepository.findAll().stream()
                .filter(a -> a.getUser() != null && a.getUser().getId().equals(user.getId()))
                .toList();
    }

    @BeforeEach
    void setUp() {
        User user = new User();
        user.setUsername("auth.test.user");
        user.setEmail("auth.test.user@pps.edu.vn");
        user.setFullName("Auth Test User");
        user.setPasswordHash(passwordEncoder.encode(RAW_PASSWORD));
        user.setStatus(User.Status.ACTIVE);
        activeUser = userRepository.save(user);
    }

    private HttpServletRequest request() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        request.addHeader("User-Agent", "junit-test");
        return request;
    }

    @Test
    void login_UC01_MainFlow_returnsTokensOnValidCredentials() {
        LoginResponse response = authService.login(
                new LoginRequest(activeUser.getUsername(), RAW_PASSWORD), request());

        assertThat(response.accessToken()).isNotBlank();
        assertThat(response.refreshToken()).isNotBlank();
        assertThat(response.accessTokenExpiresInSeconds()).isEqualTo(jwtService.getAccessTokenTtlSeconds());

        User reloaded = userRepository.findById(activeUser.getId()).orElseThrow();
        assertThat(reloaded.getFailedLoginCount()).isZero();
        assertThat(reloaded.getLastLoginAt()).isNotNull();
        assertThat(reloaded.getLockedUntil()).isNull();

        List<LoginAttempt> attempts = attemptsFor(activeUser);
        assertThat(attempts).hasSize(1);
        assertThat(attempts.get(0).isSuccess()).isTrue();
        assertThat(attempts.get(0).getFailureReason()).isNull();
    }

    @Test
    void login_UC01_A1_rejectsWrongPassword() {
        assertThatThrownBy(() -> authService.login(
                new LoginRequest(activeUser.getUsername(), "wrong-password"), request()))
                .isInstanceOf(InvalidCredentialsException.class);

        User reloaded = userRepository.findById(activeUser.getId()).orElseThrow();
        assertThat(reloaded.getFailedLoginCount()).isEqualTo(1);
        assertThat(reloaded.getLockedUntil()).isNull();

        List<LoginAttempt> attempts = attemptsFor(activeUser);
        assertThat(attempts).hasSize(1);
        assertThat(attempts.get(0).isSuccess()).isFalse();
        assertThat(attempts.get(0).getFailureReason()).isEqualTo(LoginAttempt.FailureReason.WRONG_PASSWORD);
    }

    @Test
    void login_UC01_A1_rejectsUnknownUsername() {
        assertThatThrownBy(() -> authService.login(
                new LoginRequest("khong-ton-tai", RAW_PASSWORD), request()))
                .isInstanceOf(InvalidCredentialsException.class);

        List<LoginAttempt> attempts = loginAttemptRepository.findAll().stream()
                .filter(a -> "khong-ton-tai".equals(a.getUsernameOrEmail()))
                .toList();
        assertThat(attempts).hasSize(1);
        assertThat(attempts.get(0).getUser()).isNull();
        assertThat(attempts.get(0).isSuccess()).isFalse();
        assertThat(attempts.get(0).getFailureReason()).isEqualTo(LoginAttempt.FailureReason.USER_NOT_FOUND);
    }

    @Test
    void login_UC01_A2_locksAccountAfter5FailedAttempts() {
        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> authService.login(
                    new LoginRequest(activeUser.getUsername(), "wrong-password"), request()))
                    .isInstanceOf(InvalidCredentialsException.class);
        }

        User locked = userRepository.findById(activeUser.getId()).orElseThrow();
        assertThat(locked.getFailedLoginCount()).isEqualTo(5);
        assertThat(locked.getLockedUntil()).isAfter(OffsetDateTime.now());

        // Đúng mật khẩu nhưng tài khoản đang khóa — vẫn phải từ chối (A2.3)
        assertThatThrownBy(() -> authService.login(
                new LoginRequest(activeUser.getUsername(), RAW_PASSWORD), request()))
                .isInstanceOf(AccountLockedException.class);

        List<LoginAttempt> attempts = attemptsFor(activeUser);
        assertThat(attempts).hasSize(6);
        assertThat(attempts.get(5).getFailureReason()).isEqualTo(LoginAttempt.FailureReason.USER_LOCKED);
    }

    @Test
    void login_UC01_A3_rejectsInactiveAccount() {
        activeUser.setStatus(User.Status.INACTIVE);
        userRepository.save(activeUser);

        assertThatThrownBy(() -> authService.login(
                new LoginRequest(activeUser.getUsername(), RAW_PASSWORD), request()))
                .isInstanceOf(AccountInactiveException.class);

        List<LoginAttempt> attempts = attemptsFor(activeUser);
        assertThat(attempts).hasSize(1);
        assertThat(attempts.get(0).getFailureReason()).isEqualTo(LoginAttempt.FailureReason.USER_INACTIVE);
    }

    @Test
    void getCurrentUser_returnsProfileWithRoleCodes() {
        Role teacherRole = roleRepository.findByCode("TEACHER").orElseThrow();
        UserRole userRole = new UserRole();
        userRole.setUser(activeUser);
        userRole.setRole(teacherRole);
        userRole.setAssignedBy(activeUser);
        userRoleRepository.save(userRole);

        CurrentUserResponse response = authService.getCurrentUser(activeUser.getId());

        assertThat(response.id()).isEqualTo(activeUser.getId());
        assertThat(response.username()).isEqualTo(activeUser.getUsername());
        assertThat(response.email()).isEqualTo(activeUser.getEmail());
        assertThat(response.fullName()).isEqualTo(activeUser.getFullName());
        assertThat(response.departmentName()).isNull(); // chưa gán phòng ban trong setUp()
        assertThat(response.roleCodes()).containsExactly("TEACHER");
        assertThat(response.studentId()).isNull(); // tài khoản không có hồ sơ Student liên kết
    }

    @Test
    void getCurrentUser_returnsEmptyRoleCodesWhenNoRoleAssigned() {
        CurrentUserResponse response = authService.getCurrentUser(activeUser.getId());

        assertThat(response.roleCodes()).isEmpty();
    }

    /**
     * Bổ sung (audit FE 2026-07-20): Admin FE cần biết effective permissions
     * thật của chính tài khoản đang gọi để ẩn/hiện menu/nút hành động, thay
     * vì tra bảng hardcode tĩnh phía client không đồng bộ với DB — tái dùng
     * đúng PermissionEvaluationService.getEffectivePermissions (cùng công
     * thức role_permissions ∪ user_permission_overrides dùng để enforce
     * @PreAuthorize("hasPermission(...)")).
     */
    @Test
    void getCurrentUser_boSung_includesEffectivePermissionsFromAssignedRole() {
        Role teacherRole = roleRepository.findByCode("TEACHER").orElseThrow();
        UserRole userRole = new UserRole();
        userRole.setUser(activeUser);
        userRole.setRole(teacherRole);
        userRole.setAssignedBy(activeUser);
        userRoleRepository.save(userRole);

        CurrentUserResponse response = authService.getCurrentUser(activeUser.getId());

        // lms.exercise.create được gán mặc định cho TEACHER từ V28/V62 -- xác nhận field permissions phản ánh đúng DB thật.
        assertThat(response.permissions()).contains("lms.exercise.create");
    }

    @Test
    void getCurrentUser_boSung_returnsEmptyPermissionsWhenNoRoleAssigned() {
        CurrentUserResponse response = authService.getCurrentUser(activeUser.getId());

        assertThat(response.permissions()).isEmpty();
    }

    /**
     * UC-42 tiền đề: tài khoản Học sinh tự đăng nhập cần tra ra studentId của
     * chính mình để gọi tiếp các API Portal (tương tự GET /api/portal/parent/children
     * cho Phụ huynh) — GET /api/auth/me phải trả kèm studentId khi có hồ sơ liên kết.
     */
    @Test
    void getCurrentUser_UC42_returnsStudentIdWhenAccountLinkedToStudentProfile() {
        Role studentRole = roleRepository.findByCode("STUDENT").orElseThrow();
        UserRole userRole = new UserRole();
        userRole.setUser(activeUser);
        userRole.setRole(studentRole);
        userRole.setAssignedBy(activeUser);
        userRoleRepository.save(userRole);

        Student student = new Student();
        student.setUser(activeUser);
        student.setStudentCode("HS-AUTH-TEST-1");
        student.setDateOfBirth(LocalDate.of(2012, 5, 1));
        student.setEnrollmentDate(LocalDate.now());
        student = studentRepository.save(student);

        CurrentUserResponse response = authService.getCurrentUser(activeUser.getId());

        assertThat(response.studentId()).isEqualTo(student.getId());
    }
}
