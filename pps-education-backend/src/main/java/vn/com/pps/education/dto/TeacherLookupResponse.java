package vn.com.pps.education.dto;

/**
 * UC-10 bước 3 — 1 dòng gợi ý giáo viên dạy thay. Bổ sung ngoài SDD gốc, đã
 * xác nhận với người dùng 2026-08-07: không lọc theo site, hiển thị toàn bộ
 * tài khoản mang role TEACHER. Tách riêng khỏi UserListItemResponse (UC-44)
 * vì self-service (không cần quyền user.view) và không cần lộ
 * department/isManagement/roles như màn quản trị tài khoản.
 */
public record TeacherLookupResponse(
        Long id,
        String username,
        String email,
        String fullName
) {}
