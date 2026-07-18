package vn.com.pps.education.dto;

import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;

/**
 * UC-16 Main Flow bước 2, A1: cập nhật khung chương trình. confirm=true bắt
 * buộc nếu khung đang được lớp IN_PROGRESS sử dụng (xem
 * CurriculumUpdateConfirmationRequiredException).
 */
public record UpdateCurriculumRequest(
        @NotBlank String name,
        String level,
        Integer totalPeriods,
        BigDecimal defaultGradePassThreshold,
        @NotBlank String status,
        boolean confirm
) {}
