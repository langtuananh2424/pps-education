package vn.com.pps.education.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * UC-23 Main Flow bước 1: metadata "bộ" video ôn tập. V98 (bổ sung ngoài
 * SDD gốc, đã xác nhận với người dùng 2026-08-06) — curriculumId bắt buộc,
 * CHỈ dùng lọc/tìm kiếm trong Kho Video (mirror CreateExamRequest); gán
 * cho lớp cụ thể làm riêng qua {@code assignToClass}, không còn truyền ở
 * đây. teacherType bắt buộc chọn 1 trong 2 (VIETNAMESE/FOREIGN).
 */
public record CreateReviewVideoSetRequest(
        @NotBlank String code,
        @NotBlank String title,
        @NotBlank String videoType,
        @NotNull Long curriculumId,
        @NotBlank String teacherType,
        Long subjectId,
        Integer displayOrder,
        /** V155 — Bộ thuộc Sub Topic nào trong mục lục sách (Sách/Khối -> Unit -> Sub Topic -> Bộ). Bỏ trống = chưa phân loại vào cấu trúc mới. */
        Long subTopicId
) {}
