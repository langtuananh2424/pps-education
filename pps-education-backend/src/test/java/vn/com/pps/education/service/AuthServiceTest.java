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
import vn.com.pps.education.exception.ActiveSessionExistsException;
import vn.com.pps.education.exception.InvalidCredentialsException;
import vn.com.pps.education.dto.LogoutRequest;
import vn.com.pps.education.domain.RefreshToken;
import vn.com.pps.education.repository.LoginAttemptRepository;
import vn.com.pps.education.repository.RefreshTokenRepository;
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
    private RefreshTokenRepository refreshTokenRepository;

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
                new LoginRequest(activeUser.getUsername(), RAW_PASSWORD, null, null, null, false), request());

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
                new LoginRequest(activeUser.getUsername(), "wrong-password", null, null, null, false), request()))
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
                new LoginRequest("khong-ton-tai", RAW_PASSWORD, null, null, null, false), request()))
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
                    new LoginRequest(activeUser.getUsername(), "wrong-password", null, null, null, false), request()))
                    .isInstanceOf(InvalidCredentialsException.class);
        }

        User locked = userRepository.findById(activeUser.getId()).orElseThrow();
        assertThat(locked.getFailedLoginCount()).isEqualTo(5);
        assertThat(locked.getLockedUntil()).isAfter(OffsetDateTime.now());

        // Đúng mật khẩu nhưng tài khoản đang khóa — vẫn phải từ chối (A2.3)
        assertThatThrownBy(() -> authService.login(
                new LoginRequest(activeUser.getUsername(), RAW_PASSWORD, null, null, null, false), request()))
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
                new LoginRequest(activeUser.getUsername(), RAW_PASSWORD, null, null, null, false), request()))
                .isInstanceOf(AccountInactiveException.class);

        List<LoginAttempt> attempts = attemptsFor(activeUser);
        assertThat(attempts).hasSize(1);
        assertThat(attempts.get(0).getFailureReason()).isEqualTo(LoginAttempt.FailureReason.USER_INACTIVE);
    }

    /**
     * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-13 — học sinh đăng nhập thiết bị 1
     * (refresh token còn ACTIVE), rồi thử đăng nhập tiếp thiết bị 2 mà KHÔNG đăng xuất thiết bị 1
     * trước — phải bị chặn (khác giáo viên/nhân viên, xem test allowsMultipleDevicesForNonStudentRoles).
     */
    @Test
    void login_boSung_rejectsSecondDeviceWhileStudentSessionActive() {
        makeStudent(activeUser);
        authService.login(new LoginRequest(activeUser.getUsername(), RAW_PASSWORD, null, null, null, false), request());

        assertThatThrownBy(() -> authService.login(
                new LoginRequest(activeUser.getUsername(), RAW_PASSWORD, null, null, null, false), request()))
                .isInstanceOf(ActiveSessionExistsException.class);

        // Vẫn phải ghi login_attempts=success (mật khẩu đúng, chỉ bị chặn bởi policy 1-thiết-bị — xem
        // Javadoc noRollbackFor ở AuthService#login) — không được để mất bản ghi audit.
        List<LoginAttempt> attempts = attemptsFor(activeUser);
        assertThat(attempts).hasSize(2);
        assertThat(attempts.get(1).isSuccess()).isTrue();
    }

    /**
     * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-19 — thay vì bị chặn hẳn như test
     * trên, học sinh có thể xác nhận (confirm=true, tương ứng bấm "Có" ở popup FE "Tài khoản đang
     * đăng nhập ở một nơi khác. Bạn có muốn đăng xuất?") để chủ động thu hồi phiên cũ rồi đăng nhập
     * tiếp — không cần biết mật khẩu/truy cập được thiết bị cũ để tự "Đăng xuất" trước.
     */
    @Test
    void login_boSung_forceLogoutRevokesOldSessionWhenConfirmed() {
        makeStudent(activeUser);
        LoginResponse firstDevice = authService.login(
                new LoginRequest(activeUser.getUsername(), RAW_PASSWORD, null, null, null, false), request());

        LoginResponse secondDevice = authService.login(
                new LoginRequest(activeUser.getUsername(), RAW_PASSWORD, null, null, null, true), request());
        assertThat(secondDevice.accessToken()).isNotBlank();
        assertThat(firstDevice.accessToken()).isNotBlank();

        List<RefreshToken> tokens = refreshTokenRepository.findAll().stream()
                .filter(t -> t.getUser().getId().equals(activeUser.getId()))
                .toList();
        assertThat(tokens).hasSize(2);
        assertThat(tokens).anyMatch(t -> t.getRevokedAt() != null); // thiết bị 1 bị thu hồi
        assertThat(tokens).anyMatch(t -> t.getRevokedAt() == null); // thiết bị 2 vừa đăng nhập, còn active
    }

    /** Đăng xuất thiết bị 1 (thu hồi refresh token) xong thì đăng nhập thiết bị 2 phải được cho phép lại bình thường. */
    @Test
    void login_boSung_allowsSecondDeviceAfterLogoutFromFirstDevice() {
        makeStudent(activeUser);
        LoginResponse firstDevice = authService.login(
                new LoginRequest(activeUser.getUsername(), RAW_PASSWORD, null, null, null, false), request());

        authService.logout(new LogoutRequest(firstDevice.refreshToken()));

        LoginResponse secondDevice = authService.login(
                new LoginRequest(activeUser.getUsername(), RAW_PASSWORD, null, null, null, false), request());
        assertThat(secondDevice.accessToken()).isNotBlank();
    }

    /** Rào 1-thiết-bị CHỈ áp dụng cho tài khoản Học sinh — giáo viên/nhân viên vẫn đăng nhập nhiều thiết bị cùng lúc bình thường. */
    @Test
    void login_boSung_allowsMultipleDevicesForNonStudentRoles() {
        authService.login(new LoginRequest(activeUser.getUsername(), RAW_PASSWORD, null, null, null, false), request());

        LoginResponse secondDevice = authService.login(
                new LoginRequest(activeUser.getUsername(), RAW_PASSWORD, null, null, null, false), request());
        assertThat(secondDevice.accessToken()).isNotBlank();
    }

    private void makeStudent(User user) {
        Role studentRole = roleRepository.findByCode("STUDENT").orElseThrow();
        UserRole userRole = new UserRole();
        userRole.setUser(user);
        userRole.setRole(studentRole);
        userRole.setAssignedBy(user);
        userRoleRepository.save(userRole);

        Student student = new Student();
        student.setUser(user);
        student.setStudentCode("HS-AUTH-SESSION-" + user.getId());
        student.setDateOfBirth(LocalDate.of(2012, 5, 1));
        student.setEnrollmentDate(LocalDate.now());
        studentRepository.save(student);
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
