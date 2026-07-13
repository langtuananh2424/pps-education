package vn.com.pps.education.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.domain.Department;
import vn.com.pps.education.domain.User;
import vn.com.pps.education.dto.CreateUserRequest;
import vn.com.pps.education.dto.UserResponse;
import vn.com.pps.education.exception.DuplicateUserAccountException;
import vn.com.pps.education.exception.ResourceNotFoundException;
import vn.com.pps.education.repository.DepartmentRepository;
import vn.com.pps.education.repository.UserRepository;

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
    private final PasswordEncoder passwordEncoder;

    public UserAccountService(UserRepository userRepository,
                               DepartmentRepository departmentRepository,
                               PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.departmentRepository = departmentRepository;
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

    private UserResponse toResponse(User u) {
        return new UserResponse(
                u.getId(), u.getUsername(), u.getEmail(), u.getFullName(), u.getPhone(),
                u.getDepartment() == null ? null : u.getDepartment().getId(),
                u.getStatus().name(), u.isManagement(),
                u.getPasswordHash() != null, u.getGoogleId() != null);
    }
}
