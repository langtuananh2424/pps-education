package vn.com.pps.education.dto;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/** UC-19 Main Flow bước 1-3: Giáo viên nhập điểm 1 học sinh cho 1 thành phần điểm. */
public record EnterGradeRequest(
        @NotNull Long studentId,
        @NotNull BigDecimal score,
        boolean absenceFlag,
        String teacherNote
) {}
