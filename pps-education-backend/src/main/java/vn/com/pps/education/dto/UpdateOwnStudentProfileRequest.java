package vn.com.pps.education.dto;

/**
 * UC-63: Học sinh tự cập nhật ảnh đại diện của chính mình (FR-USR-07) —
 * bổ sung ngoài SDD gốc, đã xác nhận với người dùng. Chỉ portraitUrl —
 * các field còn lại (ngày sinh, giới tính, trường/lớp gốc, trạng thái,
 * ghi chú...) mang tính hồ sơ học vụ/hành chính, vẫn chỉ Nhân viên Giáo
 * vụ/Admin sửa qua UpdateStudentRequest (UC-13).
 */
public record UpdateOwnStudentProfileRequest(
        String portraitUrl
) {}
