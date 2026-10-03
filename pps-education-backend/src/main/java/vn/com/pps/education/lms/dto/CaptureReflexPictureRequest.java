package vn.com.pps.education.lms.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * V200 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-29) — UC-23b: chụp khung hình của video (đã tải lên
 * hệ thống) tại mốc câu hỏi. Nhận URL video thay vì id vì lúc tạo bộ mới video chưa được lưu.
 */
public record CaptureReflexPictureRequest(@NotBlank String videoUrl, @NotNull @Min(0) Integer timestampSeconds) {
}
