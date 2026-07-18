package vn.com.pps.education.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** UC-13 Main Flow bước 2: liên kết 1 phụ huynh (đã có hồ sơ) với học sinh. */
public record LinkParentRequest(
        @NotNull Long parentId,
        @NotBlank String relationship,
        boolean isPrimaryContact,
        boolean isFinancialResponsible,
        String notes
) {}
