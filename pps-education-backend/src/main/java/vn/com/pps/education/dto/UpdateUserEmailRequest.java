package vn.com.pps.education.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * UC-55: Quản trị viên cập nhật email tài khoản (FR-USR-06, bổ sung ngoài
 * SDD gốc, đã xác nhận với người dùng) — tách biệt hẳn khỏi UC-49 (Postcondition
 * UC-49 quy định rõ email giữ nguyên không đổi qua luồng đó).
 */
public record UpdateUserEmailRequest(
        @NotBlank @Email @Size(max = 255) String newEmail
) {}
