package vn.com.pps.education.controller;

import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.com.pps.education.dto.AdminChangePasswordRequest;
import vn.com.pps.education.dto.CreateUserRequest;
import vn.com.pps.education.dto.UpdateUserRequest;
import vn.com.pps.education.dto.UpdateUserStatusRequest;
import vn.com.pps.education.dto.UserDetailResponse;
import vn.com.pps.education.dto.UserListItemResponse;
import vn.com.pps.education.dto.UserResponse;
import vn.com.pps.education.dto.UserSearchRequest;
import vn.com.pps.education.security.AuthenticatedUser;
import vn.com.pps.education.service.UserAccountService;

/**
 * UC-43: Khởi tạo tài khoản người dùng (FR-USR-01), UC-44: Xem/tra cứu danh
 * sách tài khoản (FR-USR-03), UC-47: Khóa/Mở khóa tài khoản (FR-USR-04),
 * UC-49: Cập nhật thông tin tài khoản (FR-USR-05) — xem Javadoc
 * UserAccountService. Bundling cả CRUD tài khoản vào 1 Controller cùng
 * pattern với RoleController (UC-03) — cùng resource /api/users, khác
 * UserPermissionOverrideController (UC-04, /api/users/{userId}/...) và
 * UserRoleController (UC-46, /api/users/{userId}/roles) vì 2 UC đó có tài
 * nguyên con riêng, lý do thay đổi khác (SOLID S).
 */
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserAccountService userAccountService;

    public UserController(UserAccountService userAccountService) {
        this.userAccountService = userAccountService;
    }

    @PreAuthorize("hasPermission(null, 'user.manage')")
    @PostMapping
    public ResponseEntity<UserResponse> create(@Valid @RequestBody CreateUserRequest request) {
        return ResponseEntity.ok(userAccountService.create(request));
    }

    /** UC-44 Main Flow bước 1-2: danh sách tài khoản, tìm kiếm/lọc tùy chọn. */
    @PreAuthorize("hasPermission(null, 'user.manage')")
    @GetMapping
    public ResponseEntity<Page<UserListItemResponse>> search(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) String status,
            @PageableDefault(size = 20) Pageable pageable) {
        var filter = new UserSearchRequest(keyword, departmentId, status);
        return ResponseEntity.ok(userAccountService.search(filter, pageable));
    }

    /** UC-44 Main Flow bước 3: chi tiết đầy đủ 1 tài khoản. */
    @PreAuthorize("hasPermission(null, 'user.manage')")
    @GetMapping("/{userId}")
    public ResponseEntity<UserDetailResponse> getDetail(@PathVariable Long userId) {
        return ResponseEntity.ok(userAccountService.getDetail(userId));
    }

    /** UC-49: cập nhật hồ sơ tài khoản (họ tên/SĐT/phòng ban/is_management). */
    @PreAuthorize("hasPermission(null, 'user.manage')")
    @PutMapping("/{userId}")
    public ResponseEntity<UserResponse> update(@PathVariable Long userId,
                                                @Valid @RequestBody UpdateUserRequest request) {
        return ResponseEntity.ok(userAccountService.update(userId, request));
    }

    /** UC-45 A4: Quản trị viên đổi mật khẩu cho một tài khoản khác. */
    @PreAuthorize("hasPermission(null, 'user.manage')")
    @PutMapping("/{userId}/password")
    public ResponseEntity<Void> changePassword(@PathVariable Long userId,
                                                @Valid @RequestBody AdminChangePasswordRequest request) {
        userAccountService.changePasswordAsAdmin(userId, request);
        return ResponseEntity.noContent().build();
    }

    /** UC-47: khóa/mở khóa tài khoản (ACTIVE/INACTIVE/SUSPENDED). */
    @PreAuthorize("hasPermission(null, 'user.manage')")
    @PutMapping("/{userId}/status")
    public ResponseEntity<UserResponse> updateStatus(@PathVariable Long userId,
                                                       @Valid @RequestBody UpdateUserStatusRequest request,
                                                       @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(userAccountService.updateStatus(userId, request, actor.userId()));
    }
}
