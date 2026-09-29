package vn.com.pps.education.dto;

import jakarta.validation.constraints.NotBlank;

/** V200 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-29) — UC-23b: nhờ AI viết nháp mô tả cho ảnh đã lưu. */
public record DraftReflexPictureBriefRequest(@NotBlank String imageUrl) {
}
