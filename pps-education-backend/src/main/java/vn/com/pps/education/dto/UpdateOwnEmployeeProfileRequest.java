package vn.com.pps.education.dto;

/**
 * UC-63: Nhân viên tự cập nhật ảnh đại diện + địa chỉ liên hệ của chính
 * mình (FR-USR-07) — bổ sung ngoài SDD gốc, đã xác nhận với người dùng.
 * Tách riêng khỏi UpdateEmployeeRequest (dùng cho UC-08, Admin/HR sửa) —
 * không nhận employeeCode/idCardNumber/employeeType/position/department/
 * status/bank info, tránh nhân viên tự sửa field nghiệp vụ/nhạy cảm.
 */
public record UpdateOwnEmployeeProfileRequest(
        String portraitUrl,
        String permanentAddress,
        String currentAddress
) {}
