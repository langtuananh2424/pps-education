package vn.com.pps.education.dto;

/** UC-43: thông tin tài khoản trả về sau khởi tạo — không bao giờ chứa password_hash. */
public record UserResponse(
        Long id,
        String username,
        String email,
        String fullName,
        String phone,
        Long departmentId,
        String status,
        boolean isManagement,
        boolean passwordSet,
        boolean googleLinked
) {}
