package vn.com.pps.education.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Kho đề — sửa được tiêu đề + teacherType/examType (V74, đã xác nhận với
 * người dùng 2026-08-04); khung chương trình bất biến sau khi tạo (mirror
 * ReviewVideoSet không đổi scope).
 */
public record UpdateExamRequest(
        @NotBlank String title,
        @NotBlank String teacherType,
        @NotBlank String examType,
        /** V144 — sửa được (thuần điều hướng/phân loại, không có workflow duyệt như curriculumId). */
        Long subTopicId
) {}
