package vn.com.pps.education.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.domain.Department;
import vn.com.pps.education.domain.RefreshToken;
import vn.com.pps.education.domain.User;
import vn.com.pps.education.dto.AdminChangePasswordRequest;
import vn.com.pps.education.dto.ChangeOwnPasswordRequest;
import vn.com.pps.education.dto.CreateUserRequest;
import vn.com.pps.education.dto.UserResponse;
import vn.com.pps.education.exception.DuplicateUserAccountException;
import vn.com.pps.education.exception.InvalidCredentialsException;
import vn.com.pps.education.exception.ResourceNotFoundException;
import vn.com.pps.education.repository.DepartmentRepository;
import vn.com.pps.education.repository.RefreshTokenRepository;
import vn.com.pps.education.repository.UserRepository;

import java.time.OffsetDateTime;
import java.util.List;

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
 * Gap có sẵn (không xử lý ở đây): SDD ghi "Có bảng users_history" nhưng
 * bảng này chưa từng được migrate và không luồng tạo user nào hiện có
 * (UC-34, UC-35, DevUserSeeder) ghi lịch sử — giữ nhất quán, sẽ bổ sung
 * đồng loạt khi có quyết định triển khai users_history.
 */
@Service
public class UserAccountService {

    private final UserRepository userRepository;
    private final DepartmentRepository departmentRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;

    public UserAccountService(UserRepository userRepository,
                               DepartmentRepository departmentRepository,
                               RefreshTokenRepository refreshTokenRepository,
                               PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.departmentRepository = departmentRepository;
        this.refreshTokenRepository = refreshTokenRepository;
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
        if (request.departmentId() != null) {
            Department department = departmentRepository.findById(request.departmentId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Không tìm thấy phòng ban id=" + request.departmentId()));
            user.setDepartment(department);
        }
        user.setManagement(Boolean.TRUE.equals(request.isManagement()));
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
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản id=" + userId));

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
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản id=" + userId));
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

        OffsetDateTime now = OffsetDateTime.now();
        List<RefreshToken> activeTokens = refreshTokenRepository.findByUserIdAndRevokedAtIsNull(user.getId());
        activeTokens.forEach(t -> t.setRevokedAt(now));
        refreshTokenRepository.saveAll(activeTokens);
    }

    private UserResponse toResponse(User u) {
        return new UserResponse(
                u.getId(), u.getUsername(), u.getEmail(), u.getFullName(), u.getPhone(),
                u.getDepartment() == null ? null : u.getDepartment().getId(),
                u.getStatus().name(), u.isManagement(),
                u.getPasswordHash() != null, u.getGoogleId() != null);
    }
}
