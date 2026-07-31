package vn.com.pps.education.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Kho đề (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-07-30): tạo 1 "Đề" mới (VD: IELTS Grade 6). */
public record CreateExamRequest(
        @NotBlank String code,
        @NotBlank String title,
        @NotNull Long curriculumId
) {}
