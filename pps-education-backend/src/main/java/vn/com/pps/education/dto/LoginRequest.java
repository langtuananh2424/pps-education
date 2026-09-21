package vn.com.pps.education.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * UC-01 bước 2: đăng nhập bằng Tài khoản/Mật khẩu.
 * screenResolution/browserLanguage/timezone: bổ sung ngoài SDD gốc (đã xác
 * nhận với người dùng 2026-09-05) — metadata thiết bị cho lịch sử đăng nhập
 * ở Quản lý người dùng, optional vì client cũ không gửi vẫn đăng nhập được.
 * confirm: bổ sung ngoài SDD gốc (đã xác nhận với người dùng 2026-09-19) —
 * cùng pattern confirm=true với UpdateCurriculumRequest/UpdateRolePermissionsRequest.
 * false (mặc định, client cũ không gửi vẫn đăng nhập được như cũ): học sinh có
 * phiên ACTIVE ở thiết bị khác bị chặn đăng nhập (ActiveSessionExistsException).
 * true: xác nhận thu hồi (revoke) phiên cũ đó rồi đăng nhập tiếp — xem
 * AuthService#requireNoActiveSessionForStudent.
 */
public record LoginRequest(
        @NotBlank String usernameOrEmail,
        @NotBlank String password,
        String screenResolution,
        String browserLanguage,
        String timezone,
        boolean confirm
) {}
