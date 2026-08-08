package vn.com.pps.education.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * "Loại giáo viên" (VIETNAMESE/FOREIGN) của 1 buổi học cụ thể — Nhận xét
 * học viên dùng để lọc/đổi nhãn dropdown BTVN buổi sau (Ngữ pháp/Bài nghe,
 * Từ Vựng (TKN)/Clip phản xạ). Bổ sung ngoài SDD gốc, đã xác nhận với
 * người dùng 2026-08-05 — mirror UpdateLessonContentRequest.
 */
public record UpdateSessionTeacherTypeRequest(@NotBlank String teacherType) {
}
