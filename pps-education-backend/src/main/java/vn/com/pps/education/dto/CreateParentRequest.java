package vn.com.pps.education.dto;

import jakarta.validation.Valid;

/**
 * UC-13 Main Flow bước 2: khởi tạo hồ sơ phụ huynh mới. Cung cấp ĐÚNG 1
 * trong 2 (kiểm tra ở StudentService):
 * - userId: gắn hồ sơ vào tài khoản đã có sẵn.
 * - newAccount: phụ huynh chưa có tài khoản — tạo tài khoản (gán role
 *   PARENT) kèm hồ sơ trong cùng 1 transaction.
 */
public record CreateParentRequest(
        Long userId,
        @Valid CreateUserRequest newAccount,
        String occupation,
        String workplace,
        String address,
        String notes
) {}
