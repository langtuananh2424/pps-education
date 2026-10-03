package vn.com.pps.education.auth.dto;

/** UC-44 Main Flow bước 2: bộ lọc tra cứu tài khoản — tất cả field đều tùy chọn. */
public record UserSearchRequest(
        String keyword,
        Long departmentId,
        String status,
        /**
         * Lọc theo mã role (VD "TEACHER") — bổ sung ngoài SDD gốc, xác nhận
         * với người dùng 2026-09-29: ô tìm giáo viên khi xếp lịch buổi học
         * trước đây lấy 8 tài khoản đầu (mọi role) rồi mới lọc TEACHER ở
         * client, nên từ khoá phổ biến ("duy", "minh") hay trả về rỗng.
         */
        String roleCode
) {}
