package vn.com.pps.education.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Kho đề (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-07-30):
 * tạo 1 "Đề" mới (VD: IELTS Grade 6). teacherType/examType bắt buộc chọn
 * 1 trong 2 (V74, bổ sung ngoài SDD gốc, đã xác nhận với người dùng
 * 2026-08-04) — teacherType: VIETNAMESE/FOREIGN; examType: REVIEW/HOMEWORK
 * (độc lập với Exercise.exerciseType, không thay thế).
 */
public record CreateExamRequest(
        @NotBlank String code,
        @NotBlank String title,
        @NotNull Long curriculumId,
        @NotBlank String teacherType,
        @NotBlank String examType,
        /** V144 — Lesson thuộc Sub Topic nào trong mục lục sách. NULL = chưa phân loại vào cấu trúc mới. */
        Long subTopicId
) {}
