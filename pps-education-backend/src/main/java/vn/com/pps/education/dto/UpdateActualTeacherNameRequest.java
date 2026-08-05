package vn.com.pps.education.dto;

import jakarta.validation.constraints.NotBlank;

/** "Tên giáo viên giảng dạy" thực tế của buổi học (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-06) — mirror UpdateLessonContentRequest. */
public record UpdateActualTeacherNameRequest(@NotBlank String actualTeacherName) {
}
