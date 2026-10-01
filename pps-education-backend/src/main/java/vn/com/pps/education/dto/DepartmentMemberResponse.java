package vn.com.pps.education.dto;

/**
 * 1 nhân sự trong danh sách thành viên/ứng viên của phòng ban (bổ sung ngoài SDD gốc, xác nhận với người
 * dùng 2026-10-01). departmentId/departmentName = phòng ban hiện tại của nhân sự (null nếu chưa thuộc phòng
 * nào) — dùng để báo trước khi thêm sẽ chuyển nhân sự khỏi phòng cũ.
 */
public record DepartmentMemberResponse(
        Long employeeId,
        Long userId,
        String employeeCode,
        String fullName,
        String positionName,
        String employeeType,
        String status,
        Long departmentId,
        String departmentName
) {}
