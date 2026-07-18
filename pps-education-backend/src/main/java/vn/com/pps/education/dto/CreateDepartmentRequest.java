package vn.com.pps.education.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Bổ sung ngoài UC cụ thể — phòng ban là danh mục cấu hình tĩnh (SDD >
 * Nền tảng > a: "Không soft-delete, không history"), cần CRUD để phục vụ
 * dropdown chọn phòng ban khi tạo nhân sự (UC-08) và các luồng khác tham
 * chiếu departments (giao việc FR-TSK-01, duyệt đơn FR-HRM-03).
 * headUserId/parentDepartmentId có thể để trống — gán sau qua PUT (SDD lưu
 * ý quan hệ vòng head_user_id <-> employees.department_id lúc seed dữ liệu).
 */
public record CreateDepartmentRequest(
        @NotBlank String code,
        @NotBlank String name,
        Long headUserId,
        Long parentDepartmentId
) {}
