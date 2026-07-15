package vn.com.pps.education.dto;

/** UC-44 Main Flow bước 2: bộ lọc tra cứu tài khoản — tất cả field đều tùy chọn. */
public record UserSearchRequest(
        String keyword,
        Long departmentId,
        String status
) {}
