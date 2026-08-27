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
        /** V140 — "GRADE_6".."GRADE_9" hoặc null/rỗng = chưa phân loại. */
        String gradeLevel,
        /** V140 — "IELTS"/"CAMBRIDGE" hoặc null/rỗng = chưa phân loại. */
        String track,
        Integer totalPeriods,
        BigDecimal defaultGradePassThreshold,
        @NotBlank String status,
        boolean confirm
) {}
