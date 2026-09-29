package vn.com.pps.education.dto;

/**
 * V200 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-29) — UC-23b: bản NHÁP mô tả tranh do AI viết,
 * giáo viên bắt buộc đối chiếu với ảnh và sửa trước khi lưu.
 *
 * @param brief        2-3 dòng mô tả (rỗng khi không thấy tranh hoặc AI lỗi).
 * @param pictureFound AI thấy có bức tranh trong ảnh.
 * @param aiAvailable  false khi gọi AI thất bại — FE báo giáo viên tự gõ mô tả.
 */
public record ReflexPictureBriefResponse(String brief, boolean pictureFound, boolean aiAvailable) {
}
