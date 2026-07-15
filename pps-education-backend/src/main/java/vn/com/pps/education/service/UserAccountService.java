package vn.com.pps.education.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.domain.Employee;
import vn.com.pps.education.domain.RefreshToken;
import vn.com.pps.education.domain.Role;
import vn.com.pps.education.domain.User;
import vn.com.pps.education.domain.UserHistory;
import vn.com.pps.education.domain.UserPermissionOverride;
import vn.com.pps.education.dto.AdminChangePasswordRequest;
import vn.com.pps.education.dto.ChangeOwnPasswordRequest;
import vn.com.pps.education.dto.CreateUserRequest;
import vn.com.pps.education.dto.RoleResponse;
import vn.com.pps.education.dto.UpdateUserRequest;
import vn.com.pps.education.dto.UpdateUserStatusRequest;
import vn.com.pps.education.dto.UserDetailResponse;
import vn.com.pps.education.dto.UserListItemResponse;
import vn.com.pps.education.dto.UserPermissionOverrideSummary;
import vn.com.pps.education.dto.UserResponse;
import vn.com.pps.education.dto.UserSearchRequest;
import vn.com.pps.education.exception.DuplicateUserAccountException;
import vn.com.pps.education.exception.InvalidCredentialsException;
import vn.com.pps.education.exception.ResourceNotFoundException;
import vn.com.pps.education.exception.SelfAccountLockException;
import vn.com.pps.education.repository.EmployeeRepository;
import vn.com.pps.education.repository.RefreshTokenRepository;
import vn.com.pps.education.repository.UserHistoryRepository;
import vn.com.pps.education.repository.UserPermissionOverrideRepository;
import vn.com.pps.education.repository.UserRepository;
import vn.com.pps.education.repository.UserRoleRepository;

import jakarta.persistence.criteria.Subquery;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * UC-43: Khởi tạo tài khoản người dùng (FR-USR-01).
 * Xem docs/uc/phan-he-02-phan-quyen.md — Main Flow bước 1-4, A1 (username/
 * email trùng), A2 (mật khẩu quá ngắn — chặn bằng bean validation ở tầng
 * Controller, @Size trên CreateUserRequest).
 *
 * Không gán role lúc tạo (Main Flow bước 4) — tài khoản mới đăng nhập được
 * nhưng danh sách quyền hiệu lực rỗng cho tới khi được gán qua UC-03/UC-04.
 *
 * Authorization qua @PreAuthorize("hasPermission(null,'user.manage')") ở
 * UserController (Hybrid PBAC). Riêng luồng UC-08 (tạo hồ sơ nhân sự kèm
 * tài khoản, quyền hrm.manage) gọi thẳng createAccount(...) từ
 * EmployeeService trong cùng transaction — xem ghi chú trong UC-08 và
 * "Cơ chế khởi tạo tài khoản" trong docs/sdd-groups/02-nen-tang.md.
 *
 * users_history (V32) chỉ ghi Action.STATUS_CHANGED (UC-47) — khớp đúng
 * phạm vi Postcondition UC-47, không mở rộng sang các luồng tạo/sửa khác
 * (UC-34, UC-35, DevUserSeeder, UC-49) vì SRS không yêu cầu audit log cho
 * các luồng đó (giống UC-45 đổi mật khẩu — không ghi users_history).
 */
@Service
public class UserAccountService {

    private final UserRepository userRepository;
    private final EmployeeRepository employeeRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRoleRepository userRoleRepository;
    private final UserPermissionOverrideRepository userPermissionOverrideRepository;
    private final UserHistoryRepository userHistoryRepository;
    private final PasswordEncoder passwordEncoder;

    public UserAccountService(UserRepository userRepository,
                               EmployeeRepository employeeRepository,
                               RefreshTokenRepository refreshTokenRepository,
                               UserRoleRepository userRoleRepository,
                               UserPermissionOverrideRepository userPermissionOverrideRepository,
                               UserHistoryRepository userHistoryRepository,
                               PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.employeeRepository = employeeRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.userRoleRepository = userRoleRepository;
        this.userPermissionOverrideRepository = userPermissionOverrideRepository;
        this.userHistoryRepository = userHistoryRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /** Main Flow bước 1-4 — bản trả DTO cho Controller. */
    @Transactional
    public UserResponse create(CreateUserRequest request) {
        return toResponse(createAccount(request));
    }

    /**
     * Main Flow bước 1-4 — bản trả entity cho Service khác phối hợp trong
     * cùng transaction (EmployeeService, UC-08 bước 1). KHÔNG gọi từ
     * Controller (Controller không được chạm entity).
     */
    @Transactional
    public User createAccount(CreateUserRequest request) {
        // A1 -- username hoặc email đã tồn tại: từ chối, báo rõ trường bị trùng.
        userRepository.findByUsername(request.username()).ifPresent(existing -> {
            throw new DuplicateUserAccountException("Username đã tồn tại: " + request.username());
        });
        userRepository.findByEmail(request.email()).ifPresent(existing -> {
            throw new DuplicateUserAccountException("Email đã tồn tại: " + request.email());
        });

        User user = new User();
        user.setUsername(request.username());
        user.setEmail(request.email());
        user.setFullName(request.fullName());
        user.setPhone(request.phone());
        // Main Flow bước 2 -- password null = tài khoản chỉ đăng nhập Google (UC-01 A4).
        if (request.password() != null) {
            user.setPasswordHash(passwordEncoder.encode(request.password()));
        }
        user.setStatus(User.Status.ACTIVE);
        return userRepository.save(user);
    }

    /**
     * UC-45: Đổi mật khẩu (FR-USR-02), luồng tự đổi mật khẩu của chính mình.
     * Xem docs/uc/phan-he-02-phan-quyen.md — Main Flow, A1 (sai mật khẩu
     * hiện tại), A3 (tài khoản chỉ đăng nhập Google, chưa từng có mật khẩu —
     * bỏ qua bước xác thực, đặt mật khẩu lần đầu). A2 (mật khẩu mới quá
     * ngắn) chặn bằng bean validation ở ChangeOwnPasswordRequest.
     */
    @Transactional
    public void changeOwnPassword(Long userId, ChangeOwnPasswordRequest request) {
        User user = getUserOrThrow(userId);

        // A3 -- tài khoản chưa từng có mật khẩu (chỉ đăng nhập Google): đặt mật khẩu lần đầu, không cần xác thực.
        if (user.getPasswordHash() != null) {
            // A1 -- mật khẩu hiện tại không đúng.
            if (request.currentPassword() == null
                    || !passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
                throw new InvalidCredentialsException("Mật khẩu hiện tại không đúng.");
            }
        }
        applyNewPassword(user, request.newPassword());
    }

    /**
     * UC-45: Đổi mật khẩu (FR-USR-02), luồng A4 — Quản trị viên (quyền
     * user.manage) đổi mật khẩu cho một tài khoản khác, không cần biết mật
     * khẩu hiện tại của tài khoản đó. A2 chặn bằng bean validation ở
     * AdminChangePasswordRequest.
     */
    @Transactional
    public void changePasswordAsAdmin(Long userId, AdminChangePasswordRequest request) {
        User user = getUserOrThrow(userId);
        applyNewPassword(user, request.newPassword());
    }

    /**
     * Postcondition UC-45 (cả 2 luồng): cập nhật password_hash + thu hồi
     * toàn bộ refresh token đang hoạt động của tài khoản — đăng xuất khỏi
     * mọi thiết bị, bắt buộc đăng nhập lại bằng mật khẩu mới.
     */
    private void applyNewPassword(User user, String newPassword) {
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        revokeAllActiveTokens(user);
    }

    private void revokeAllActiveTokens(User user) {
        OffsetDateTime now = OffsetDateTime.now();
        List<RefreshToken> activeTokens = refreshTokenRepository.findByUserIdAndRevokedAtIsNull(user.getId());
        activeTokens.forEach(t -> t.setRevokedAt(now));
        refreshTokenRepository.saveAll(activeTokens);
    }

    /**
     * UC-44: Xem/tra cứu danh sách tài khoản (FR-USR-03).
     * Xem docs/uc/phan-he-02-phan-quyen.md — Main Flow bước 1-2 (danh sách +
     * tìm kiếm/lọc), A1 (không có kết quả = trang rỗng, không phải lỗi).
     * Mỗi dòng kèm danh sách role hiện tại (Main Flow bước 1) — batch-load
     * theo user_id để tránh N+1.
     */
    @Transactional(readOnly = true)
    public Page<UserListItemResponse> search(UserSearchRequest filter, Pageable pageable) {
        Specification<User> spec = buildSpecification(filter);
        Pageable sorted = pageable.getSort().isSorted()
                ? pageable
                : PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), Sort.by(Sort.Direction.ASC, "username"));
        Page<User> page = userRepository.findAll(spec, sorted);

        List<Long> userIds = page.getContent().stream().map(User::getId).toList();
        Map<Long, List<RoleResponse>> rolesByUser = userRoleRepository.findByUserIdIn(userIds).stream()
                .collect(Collectors.groupingBy(ur -> ur.getUser().getId(),
                        Collectors.mapping(ur -> toRoleResponse(ur.getRole()), Collectors.toList())));
        Map<Long, Employee> employeeByUserId = employeeRepository.findByUserIdIn(userIds).stream()
                .collect(Collectors.toMap(e -> e.getUser().getId(), e -> e));

        return page.map(u -> toListItem(u, rolesByUser.getOrDefault(u.getId(), List.of()), employeeByUserId.get(u.getId())));
    }

    private Specification<User> buildSpecification(UserSearchRequest filter) {
        Specification<User> spec = Specification.where(null);
        if (filter.keyword() != null && !filter.keyword().isBlank()) {
            String pattern = "%" + filter.keyword().toLowerCase() + "%";
            spec = spec.and((root, query, cb) -> cb.or(
                    cb.like(cb.lower(root.get("username")), pattern),
                    cb.like(cb.lower(root.get("email")), pattern),
                    cb.like(cb.lower(root.get("fullName")), pattern)));
        }
        if (filter.departmentId() != null) {
            // department_id nay thuộc employees — lọc qua EXISTS subquery correlate theo user.
            spec = spec.and((root, query, cb) -> {
                Subquery<Long> sq = query.subquery(Long.class);
                var employeeRoot = sq.from(Employee.class);
                sq.select(employeeRoot.get("id"))
                        .where(cb.equal(employeeRoot.get("user"), root),
                                cb.equal(employeeRoot.get("department").get("id"), filter.departmentId()));
                return cb.exists(sq);
            });
        }
        if (filter.status() != null && !filter.status().isBlank()) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), User.Status.valueOf(filter.status())));
        }
        return spec;
    }

    /** UC-44 Main Flow bước 3: chi tiết đầy đủ 1 tài khoản (role + permission override). */
    @Transactional(readOnly = true)
    public UserDetailResponse getDetail(Long userId) {
        User user = getUserOrThrow(userId);

        List<RoleResponse> roles = userRoleRepository.findByUserId(userId).stream()
                .map(ur -> toRoleResponse(ur.getRole()))
                .sorted(Comparator.comparing(RoleResponse::code))
                .toList();
        List<UserPermissionOverrideSummary> overrides = userPermissionOverrideRepository.findByUserId(userId).stream()
                .map(this::toOverrideSummary)
                .toList();
        Employee employee = employeeRepository.findByUserId(userId).orElse(null);

        return toDetailResponse(user, roles, overrides, employee);
    }

    /**
     * UC-49: Cập nhật thông tin tài khoản (FR-USR-05).
     * Xem docs/uc/phan-he-02-phan-quyen.md — Main Flow. KHÔNG đổi
     * username/email/mật khẩu/trạng thái — ngoài phạm vi UC-49 (thuộc
     * UC-43/UC-45/UC-47). KHÔNG đổi phòng ban/cờ miễn trừ quản lý — 2
     * trường đó thuộc hồ sơ nhân sự, sửa qua UC-08 (EmployeeService).
     */
    @Transactional
    public UserResponse update(Long userId, UpdateUserRequest request) {
        User user = getUserOrThrow(userId);
        user.setFullName(request.fullName());
        user.setPhone(request.phone());
        return toResponse(userRepository.save(user));
    }

    /**
     * UC-47: Khóa/Mở khóa tài khoản (FR-USR-04).
     * Xem docs/uc/phan-he-02-phan-quyen.md — Main Flow bước 2-4, A1 (khôi
     * phục về ACTIVE — reset failed_login_count/locked_until), A2 (không
     * thể tự khóa tài khoản của chính mình).
     */
    @Transactional
    public UserResponse updateStatus(Long userId, UpdateUserStatusRequest request, Long actorUserId) {
        User user = getUserOrThrow(userId);
        User.Status newStatus = User.Status.valueOf(request.status());

        // A2 -- không thể tự khóa tài khoản của chính mình đang đăng nhập.
        if (userId.equals(actorUserId) && newStatus != User.Status.ACTIVE) {
            throw new SelfAccountLockException("Không thể tự khóa tài khoản của chính mình.");
        }

        User.Status oldStatus = user.getStatus();
        if (oldStatus == newStatus) {
            return toResponse(user); // Idempotent -- không đổi gì thì không ghi log/thu hồi token thừa.
        }

        user.setStatus(newStatus);
        if (newStatus == User.Status.ACTIVE) {
            // A1 -- khôi phục tài khoản: reset đếm đăng nhập sai + bỏ khóa tạm.
            user.setFailedLoginCount(0);
            user.setLockedUntil(null);
        } else {
            // Main Flow bước 4 -- thu hồi toàn bộ refresh token đang hoạt động, buộc đăng xuất mọi thiết bị.
            revokeAllActiveTokens(user);
        }
        userRepository.save(user);

        User actor = getUserOrThrow(actorUserId);
        writeStatusHistory(user, actor, oldStatus, newStatus);

        return toResponse(user);
    }

    /** Postcondition UC-47: users_history ghi ai đổi trạng thái tài khoản nào, khi nào, từ giá trị gì sang giá trị gì. */
    private void writeStatusHistory(User target, User actor, User.Status oldStatus, User.Status newStatus) {
        UserHistory history = new UserHistory();
        history.setUser(target);
        history.setChangedBy(actor);
        history.setAction(UserHistory.Action.STATUS_CHANGED);
        Map<String, Object> details = new HashMap<>();
        details.put("oldStatus", oldStatus.name());
        details.put("newStatus", newStatus.name());
        history.setDetails(details);
        userHistoryRepository.save(history);
    }

    private User getUserOrThrow(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản id=" + userId));
    }

    private UserResponse toResponse(User u) {
        Employee employee = employeeRepository.findByUserId(u.getId()).orElse(null);
        return new UserResponse(
                u.getId(), u.getUsername(), u.getEmail(), u.getFullName(), u.getPhone(),
                employee == null || employee.getDepartment() == null ? null : employee.getDepartment().getId(),
                u.getStatus().name(), employee != null && employee.isManagement(),
                u.getPasswordHash() != null, u.getGoogleId() != null);
    }

    private UserListItemResponse toListItem(User u, List<RoleResponse> roles, Employee employee) {
        return new UserListItemResponse(
                u.getId(), u.getUsername(), u.getEmail(), u.getFullName(), u.getPhone(),
                employee == null || employee.getDepartment() == null ? null : employee.getDepartment().getId(),
                u.getStatus().name(), employee != null && employee.isManagement(), roles);
    }

    private UserDetailResponse toDetailResponse(User u, List<RoleResponse> roles,
                                                 List<UserPermissionOverrideSummary> overrides, Employee employee) {
        return new UserDetailResponse(
                u.getId(), u.getUsername(), u.getEmail(), u.getFullName(), u.getPhone(),
                employee == null || employee.getDepartment() == null ? null : employee.getDepartment().getId(),
                u.getStatus().name(), employee != null && employee.isManagement(),
                u.getPasswordHash() != null, u.getGoogleId() != null,
                u.getLastLoginAt(), u.getFailedLoginCount(), u.getLockedUntil(),
                roles, overrides);
    }

    private RoleResponse toRoleResponse(Role role) {
        return new RoleResponse(role.getId(), role.getCode(), role.getName(), role.getDescription(), role.isSystem());
    }

    private UserPermissionOverrideSummary toOverrideSummary(UserPermissionOverride o) {
        return new UserPermissionOverrideSummary(
                o.getPermission().getId(), o.getPermission().getCode(),
                o.getOverrideType().name(), o.getReason(), o.getExpiresAt());
    }
}
