package vn.com.pps.education.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * UC-16b Main Flow bước 1-2: tạo bản sao tùy biến từ khung chương trình
 * gốc. Nội dung (name/level/totalPeriods/defaultGradePassThreshold) được
 * sao chép từ bản gốc — chỉnh sửa qua updateCustomCopy (Main Flow bước
 * 3). name có thể ghi đè ngay lúc tạo nếu muốn đặt tên riêng.
 */
public record CreateCustomCurriculumRequest(
        @NotBlank String code,
        @NotNull Long parentCurriculumId,
        @NotNull Long siteId,
        String name
) {}
