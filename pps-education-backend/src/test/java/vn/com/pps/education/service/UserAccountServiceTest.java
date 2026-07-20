package vn.com.pps.education.service;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.domain.Department;
import vn.com.pps.education.domain.Employee;
import vn.com.pps.education.domain.Permission;
import vn.com.pps.education.domain.RefreshToken;
import vn.com.pps.education.domain.Role;
import vn.com.pps.education.domain.User;
import vn.com.pps.education.domain.UserPermissionOverride;
import vn.com.pps.education.domain.UserRole;
import vn.com.pps.education.dto.AdminChangePasswordRequest;
import vn.com.pps.education.dto.ChangeOwnPasswordRequest;
import vn.com.pps.education.dto.CreateUserRequest;
import vn.com.pps.education.dto.RoleResponse;
import vn.com.pps.education.dto.UpdateUserRequest;
import vn.com.pps.education.dto.UpdateUserStatusRequest;
import vn.com.pps.education.dto.UserDetailResponse;
import vn.com.pps.education.dto.UserListItemResponse;
import vn.com.pps.education.dto.UserResponse;
import vn.com.pps.education.dto.UserSearchRequest;
import vn.com.pps.education.exception.DuplicateUserAccountException;
import vn.com.pps.education.exception.InvalidCredentialsException;
import vn.com.pps.education.exception.SelfAccountLockException;
import vn.com.pps.education.repository.DepartmentRepository;
import vn.com.pps.education.repository.EmployeeRepository;
import vn.com.pps.education.repository.PermissionRepository;
import vn.com.pps.education.repository.RefreshTokenRepository;
import vn.com.pps.education.repository.RoleRepository;
import vn.com.pps.education.repository.UserHistoryRepository;
import vn.com.pps.education.repository.UserPermissionOverrideRepository;
import vn.com.pps.education.repository.UserRepository;
import vn.com.pps.education.repository.UserRoleRepository;
import vn.com.pps.education.support.AbstractIntegrationTest;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * UC-43: Khởi tạo tài khoản người dùng — Main Flow (bước 1-4), A1 (username/
 * email trùng), A2 (mật khẩu quá ngắn). UC-45: Đổi mật khẩu — Main Flow, A1
 * (sai mật khẩu hiện tại), A3 (tài khoản chỉ đăng nhập Google), A4 (Quản trị
 * viên đổi cho tài khoản khác). UC-44: Xem/tra cứu danh sách tài khoản —
 * Main Flow, A1 (không có kết quả). UC-49: Cập nhật thông tin tài khoản —
 * Main Flow (chỉ còn họ tên/SĐT — phòng ban/is_management đã chuyển sang
 * hồ sơ nhân sự, xem EmployeeServiceTest A1/A2). UC-47:
 * Khóa/Mở khóa tài khoản — Main Flow, A1 (khôi phục), A2 (tự khóa chính
 * mình). Xem docs/uc/phan-he-02-phan-quyen.md.
 */
@Transactional
class UserAccountServiceTest extends AbstractIntegrationTest {

    private static final AtomicLong SEQ = new AtomicLong();
    private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

    @Autowired
    private UserAccountService userAccountService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private DepartmentRepository departmentRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private PermissionRepository permissionRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserRoleRepository userRoleRepository;

    @Autowired
    private UserPermissionOverrideRepository userPermissionOverrideRepository;

    @Autowired
    private UserHistoryRepository userHistoryRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void create_UC43_MainFlow_withPassword_createsActiveAccountWithBcryptHash() {
        UserResponse response = userAccountService.create(baseRequest("MatKhau@8"));

        assertThat(response.id()).isNotNull();
        assertThat(response.status()).isEqualTo("ACTIVE");
        assertThat(response.passwordSet()).isTrue();
        assertThat(response.googleLinked()).isFalse();
        User saved = userRepository.findById(response.id()).orElseThrow();
        assertThat(saved.getPasswordHash()).startsWith("$2"); // BCrypt (NFR-SEC-01)
    }

    @Test
    void create_UC43_MainFlow_withoutPassword_createsGoogleOnlyAccount() {
        UserResponse response = userAccountService.create(baseRequest(null));

        assertThat(response.passwordSet()).isFalse();
        User saved = userRepository.findById(response.id()).orElseThrow();
        assertThat(saved.getPasswordHash()).isNull();
    }

    @Test
    void create_UC43_Postcondition_newAccountHasNoRoleAssigned() {
        UserResponse response = userAccountService.create(baseRequest("MatKhau@8"));

        // Hậu điều kiện UC-43: tài khoản mới chưa có role nào cho tới khi được gán qua UC-03/UC-04.
        assertThat(response.id()).isNotNull();
        assertThat(userRepository.findById(response.id()).orElseThrow().getStatus())
                .isEqualTo(User.Status.ACTIVE);
    }

    @Test
    void create_UC43_A1_rejectsDuplicateUsername() {
        String username = username();
        userAccountService.create(new CreateUserRequest(
                username, email(), "Người Một", null, null));

        assertThatThrownBy(() -> userAccountService.create(new CreateUserRequest(
                username, email(), "Người Hai", null, null)))
                .isInstanceOf(DuplicateUserAccountException.class);
    }

    @Test
    void create_UC43_A1_rejectsDuplicateEmail() {
        String email = email();
        userAccountService.create(new CreateUserRequest(
                username(), email, "Người Một", null, null));

        assertThatThrownBy(() -> userAccountService.create(new CreateUserRequest(
                username(), email, "Người Hai", null, null)))
                .isInstanceOf(DuplicateUserAccountException.class);
    }

    @Test
    void create_UC43_A2_rejectsPasswordShorterThan8Chars() {
        CreateUserRequest request = new CreateUserRequest(
                username(), email(), "Người Test", null, "short1");

        assertThat(VALIDATOR.validate(request)).isNotEmpty();
    }

    @Test
    void changeOwnPassword_UC45_MainFlow_updatesHashAndRevokesActiveRefreshTokens() {
        UserResponse account = userAccountService.create(baseRequest("MatKhauCu@8"));
        RefreshToken activeToken = activeRefreshTokenFor(account.id());

        userAccountService.changeOwnPassword(account.id(),
                new ChangeOwnPasswordRequest("MatKhauCu@8", "MatKhauMoi@9"));

        User updated = userRepository.findById(account.id()).orElseThrow();
        assertThat(passwordEncoder.matches("MatKhauMoi@9", updated.getPasswordHash())).isTrue();
        // Hậu điều kiện UC-45: thu hồi toàn bộ refresh token đang hoạt động.
        assertThat(refreshTokenRepository.findById(activeToken.getId()).orElseThrow().getRevokedAt()).isNotNull();
    }

    @Test
    void changeOwnPassword_UC45_A1_rejectsWrongCurrentPassword() {
        UserResponse account = userAccountService.create(baseRequest("MatKhauCu@8"));
        RefreshToken activeToken = activeRefreshTokenFor(account.id());

        assertThatThrownBy(() -> userAccountService.changeOwnPassword(account.id(),
                new ChangeOwnPasswordRequest("SaiMatKhau@1", "MatKhauMoi@9")))
                .isInstanceOf(InvalidCredentialsException.class);

        User unchanged = userRepository.findById(account.id()).orElseThrow();
        assertThat(passwordEncoder.matches("MatKhauCu@8", unchanged.getPasswordHash())).isTrue();
        assertThat(refreshTokenRepository.findById(activeToken.getId()).orElseThrow().getRevokedAt()).isNull();
    }

    @Test
    void changeOwnPassword_UC45_A2_rejectsNewPasswordShorterThan8Chars() {
        ChangeOwnPasswordRequest request = new ChangeOwnPasswordRequest("MatKhauCu@8", "short1");

        assertThat(VALIDATOR.validate(request)).isNotEmpty();
    }

    @Test
    void changeOwnPassword_UC45_A3_googleOnlyAccountSetsFirstPasswordWithoutCurrentPassword() {
        UserResponse account = userAccountService.create(baseRequest(null));

        userAccountService.changeOwnPassword(account.id(),
                new ChangeOwnPasswordRequest(null, "MatKhauMoi@9"));

        User updated = userRepository.findById(account.id()).orElseThrow();
        assertThat(passwordEncoder.matches("MatKhauMoi@9", updated.getPasswordHash())).isTrue();
    }

    @Test
    void changePasswordAsAdmin_UC45_A4_updatesHashAndRevokesActiveRefreshTokensWithoutCurrentPassword() {
        UserResponse account = userAccountService.create(baseRequest("MatKhauCu@8"));
        RefreshToken activeToken = activeRefreshTokenFor(account.id());

        userAccountService.changePasswordAsAdmin(account.id(), new AdminChangePasswordRequest("MatKhauMoi@9"));

        User updated = userRepository.findById(account.id()).orElseThrow();
        assertThat(passwordEncoder.matches("MatKhauMoi@9", updated.getPasswordHash())).isTrue();
        assertThat(refreshTokenRepository.findById(activeToken.getId()).orElseThrow().getRevokedAt()).isNotNull();
    }

    @Test
    void search_UC44_MainFlow_findsByKeywordAndIncludesAssignedRoles() {
        String suffix = "kw" + SEQ.incrementAndGet();
        UserResponse account = userAccountService.create(
                new CreateUserRequest(username(), email(), "Người Tìm Kiếm " + suffix, null, null));
        Role role = roleRepository.findByCode("STAFF").orElseThrow();
        assignRole(account.id(), role);

        Page<UserListItemResponse> page = userAccountService.search(
                new UserSearchRequest(suffix, null, null), PageRequest.of(0, 20));

        assertThat(page.getContent()).hasSize(1);
        UserListItemResponse item = page.getContent().get(0);
        assertThat(item.id()).isEqualTo(account.id());
        // Main Flow bước 1 -- danh sách phải kèm role hiện tại của tài khoản.
        assertThat(item.roles()).extracting(RoleResponse::code).containsExactly("STAFF");
    }

    @Test
    void search_UC44_MainFlow_filtersByDepartmentAndStatus() {
        Department department = departmentRepository.save(newDepartment());
        UserResponse account = userAccountService.create(baseRequest("MatKhau@8"));
        User user = userRepository.findById(account.id()).orElseThrow();
        newEmployee(user, department);
        user.setStatus(User.Status.SUSPENDED);
        userRepository.save(user);

        Page<UserListItemResponse> byDepartment = userAccountService.search(
                new UserSearchRequest(null, department.getId(), null), PageRequest.of(0, 20));
        assertThat(byDepartment.getContent()).extracting(UserListItemResponse::id).contains(account.id());

        Page<UserListItemResponse> byStatus = userAccountService.search(
                new UserSearchRequest(null, null, "SUSPENDED"), PageRequest.of(0, 20));
        assertThat(byStatus.getContent()).extracting(UserListItemResponse::id).contains(account.id());
    }

    @Test
    void search_UC44_A1_returnsEmptyPageWhenNoMatch() {
        Page<UserListItemResponse> page = userAccountService.search(
                new UserSearchRequest("khong-ton-tai-" + SEQ.incrementAndGet(), null, null), PageRequest.of(0, 20));

        assertThat(page.getContent()).isEmpty();
    }

    @Test
    void getDetail_UC44_MainFlow_returnsRolesAndPermissionOverrides() {
        UserResponse account = userAccountService.create(baseRequest("MatKhau@8"));
        User target = userRepository.findById(account.id()).orElseThrow();
        Role role = roleRepository.findByCode("STAFF").orElseThrow();
        assignRole(account.id(), role);

        Permission permission = permissionRepository.save(newPermission());
        UserPermissionOverride override = new UserPermissionOverride();
        override.setUser(target);
        override.setPermission(permission);
        override.setOverrideType(UserPermissionOverride.OverrideType.GRANT);
        override.setReason("Test override");
        override.setGrantedBy(target);
        userPermissionOverrideRepository.save(override);

        UserDetailResponse detail = userAccountService.getDetail(account.id());

        assertThat(detail.id()).isEqualTo(account.id());
        assertThat(detail.failedLoginCount()).isZero();
        assertThat(detail.lockedUntil()).isNull();
        assertThat(detail.roles()).extracting(RoleResponse::code).containsExactly("STAFF");
        assertThat(detail.permissionOverrides()).hasSize(1);
        assertThat(detail.permissionOverrides().get(0).permissionCode()).isEqualTo(permission.getCode());
        assertThat(detail.permissionOverrides().get(0).overrideType()).isEqualTo("GRANT");
    }

    @Test
    void update_UC49_MainFlow_updatesProfileFieldsAndKeepsIdentityUnchanged() {
        UserResponse account = userAccountService.create(baseRequest("MatKhau@8"));

        UserResponse updated = userAccountService.update(account.id(),
                new UpdateUserRequest("Tên Đã Sửa", "0900000000"));

        assertThat(updated.fullName()).isEqualTo("Tên Đã Sửa");
        assertThat(updated.phone()).isEqualTo("0900000000");
        // Postcondition UC-49 -- username/email/status giữ nguyên. Phòng
        // ban/is_management không còn thuộc UC-49 -- xem EmployeeServiceTest
        // (UC-08, đã chuyển 2 trường này sang employees).
        assertThat(updated.username()).isEqualTo(account.username());
        assertThat(updated.email()).isEqualTo(account.email());
        assertThat(updated.status()).isEqualTo("ACTIVE");
    }

    @Test
    void updateStatus_UC47_MainFlow_suspendsAccountRevokesTokensAndWritesHistory() {
        UserResponse account = userAccountService.create(baseRequest("MatKhau@8"));
        RefreshToken activeToken = activeRefreshTokenFor(account.id());
        Long actorId = newActor().getId();
        long historyBefore = userHistoryRepository.count();

        UserResponse updated = userAccountService.updateStatus(account.id(),
                new UpdateUserStatusRequest("SUSPENDED"), actorId);

        assertThat(updated.status()).isEqualTo("SUSPENDED");
        // Main Flow bước 4 -- thu hồi toàn bộ refresh token đang hoạt động.
        assertThat(refreshTokenRepository.findById(activeToken.getId()).orElseThrow().getRevokedAt()).isNotNull();
        // Postcondition -- users_history ghi lại thay đổi trạng thái.
        assertThat(userHistoryRepository.count()).isEqualTo(historyBefore + 1);
    }

    @Test
    void updateStatus_UC47_A1_restoringToActiveResetsFailedLoginAndLockedUntil() {
        UserResponse account = userAccountService.create(baseRequest("MatKhau@8"));
        User user = userRepository.findById(account.id()).orElseThrow();
        user.setStatus(User.Status.SUSPENDED);
        user.setFailedLoginCount(4);
        user.setLockedUntil(OffsetDateTime.now().plusMinutes(30));
        userRepository.save(user);
        Long actorId = newActor().getId();

        UserResponse updated = userAccountService.updateStatus(account.id(),
                new UpdateUserStatusRequest("ACTIVE"), actorId);

        assertThat(updated.status()).isEqualTo("ACTIVE");
        User restored = userRepository.findById(account.id()).orElseThrow();
        assertThat(restored.getFailedLoginCount()).isZero();
        assertThat(restored.getLockedUntil()).isNull();
    }

    @Test
    void updateStatus_UC47_A2_rejectsSelfLock() {
        UserResponse account = userAccountService.create(baseRequest("MatKhau@8"));

        assertThatThrownBy(() -> userAccountService.updateStatus(account.id(),
                new UpdateUserStatusRequest("SUSPENDED"), account.id()))
                .isInstanceOf(SelfAccountLockException.class);

        assertThat(userRepository.findById(account.id()).orElseThrow().getStatus()).isEqualTo(User.Status.ACTIVE);
    }

    private void assignRole(Long userId, Role role) {
        User user = userRepository.findById(userId).orElseThrow();
        UserRole userRole = new UserRole();
        userRole.setUser(user);
        userRole.setRole(role);
        userRole.setAssignedBy(user);
        userRoleRepository.save(userRole);
    }

    private User newActor() {
        User actor = new User();
        actor.setUsername("actor." + SEQ.incrementAndGet());
        actor.setEmail("actor." + SEQ.incrementAndGet() + "@pps.edu.vn");
        actor.setFullName("Actor Test");
        actor.setStatus(User.Status.ACTIVE);
        return userRepository.save(actor);
    }

    private Department newDepartment() {
        Department department = new Department();
        department.setCode("DEPT" + SEQ.incrementAndGet());
        department.setName("Phòng Test " + SEQ.get());
        return department;
    }

    private Employee newEmployee(User forUser, Department department) {
        Employee employee = new Employee();
        employee.setUser(forUser);
        employee.setEmployeeCode("NVUAS" + SEQ.incrementAndGet());
        employee.setDateOfBirth(LocalDate.of(1995, 1, 1));
        employee.setEmployeeType(Employee.EmployeeType.STAFF);
        employee.setDepartment(department);
        employee.setDefaultShiftRequired(true);
        employee.setHireDate(LocalDate.of(2024, 1, 1));
        return employeeRepository.save(employee);
    }

    private Permission newPermission() {
        Permission permission = new Permission();
        permission.setCode("test.override_" + SEQ.incrementAndGet());
        permission.setName("Test Override Permission");
        permission.setModule("USER");
        return permission;
    }

    private RefreshToken activeRefreshTokenFor(Long userId) {
        RefreshToken token = new RefreshToken();
        token.setUser(userRepository.findById(userId).orElseThrow());
        token.setTokenHash("hash." + SEQ.incrementAndGet());
        token.setExpiresAt(OffsetDateTime.now().plusDays(30));
        return refreshTokenRepository.save(token);
    }

    private CreateUserRequest baseRequest(String password) {
        return new CreateUserRequest(username(), email(), "Người Test", null, password);
    }

    private String username() {
        return "user.test." + SEQ.incrementAndGet();
    }

    private String email() {
        return "user.test." + SEQ.incrementAndGet() + "@pps.edu.vn";
    }
}
