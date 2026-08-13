package vn.com.pps.education.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalTime;

/** UC-48: Xếp lịch buổi học (FR-ACA-05) — xem Javadoc ClassSessionService. */
public record CreateClassSessionRequest(
        @NotNull LocalDate sessionDate,
        @NotNull LocalTime startTime,
        @NotNull LocalTime endTime,
        Long roomId,
        @NotBlank String sessionType,
        /**
         * Loại giáo viên (VIETNAMESE/FOREIGN) — bắt buộc. Giáo viên phụ
         * trách buổi được hệ thống TỰ ĐỘNG suy ra từ giáo viên chính
         * (PRIMARY) đang active của lớp cùng loại này, không nhập tay
         * (bổ sung ngoài SDD gốc, xác nhận 2026-08-13).
         */
        @NotBlank String teacherType,
        /** Bắt buộc khi sessionType=MAKEUP (buổi này bù cho buổi nào); phải để trống với loại khác. */
        Long makeupForSessionId
) {}
