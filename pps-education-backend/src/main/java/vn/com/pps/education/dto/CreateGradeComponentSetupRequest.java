package vn.com.pps.education.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/** UC-19 Precondition: cấu hình sổ điểm cho 1 (lớp, kỳ học, Giữa/Cuối kỳ) — V95, thay CreateGradePeriodRequest. */
public record CreateGradeComponentSetupRequest(
        @NotNull Long academicTermId,
        @NotBlank String evaluationType, // MID_TERM / END_TERM
        @NotBlank String scaleType, // POINT_10 / PERCENT / IELTS — V97
        @NotNull LocalDate rosterAsOfDate,
        boolean commentRequired
) {}
