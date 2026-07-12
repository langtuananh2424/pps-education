package vn.com.pps.education.dto;

/** UC-25 Main Flow bước 2: 1 con của Phụ huynh đang đăng nhập. */
public record ChildResponse(
        Long studentId,
        String studentFullName,
        String studentCode
) {}
