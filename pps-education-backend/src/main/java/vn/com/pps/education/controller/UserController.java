package vn.com.pps.education.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.com.pps.education.dto.CreateUserRequest;
import vn.com.pps.education.dto.UserResponse;
import vn.com.pps.education.service.UserAccountService;

/**
 * UC-43: Khởi tạo tài khoản người dùng (FR-USR-01) — xem Javadoc
 * UserAccountService. Tách khỏi UserPermissionOverrideController (UC-04,
 * /api/users/{userId}/...) — 2 UC khác lý do thay đổi (SOLID S).
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
}
