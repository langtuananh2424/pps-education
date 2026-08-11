package vn.com.pps.education.dto;

import jakarta.validation.constraints.NotBlank;

/** UC-67 bước 5: sửa metadata mẫu báo cáo (không kèm file mới — xem UpdateReportTemplateFileRequest riêng nếu cần thay file). */
public record UpdateReportTemplateRequest(
        @NotBlank String name,
        String description
) {}
