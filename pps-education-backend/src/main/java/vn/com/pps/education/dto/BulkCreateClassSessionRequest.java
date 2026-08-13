package vn.com.pps.education.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * UC-56: Sinh lịch học hàng loạt theo mẫu lặp (FR-ACA-05, bổ sung ngoài
 * SDD gốc, đã xác nhận với người dùng). daysOfWeek dùng đúng tên hằng số
 * java.time.DayOfWeek (MONDAY..SUNDAY). Với mỗi ngày trong [startDate,
 * endDate] khớp daysOfWeek, hệ thống thử tạo 1 buổi — xem Javadoc
 * ClassSessionService.bulkCreateSessions.
 */
public record BulkCreateClassSessionRequest(
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate,
        @NotEmpty List<String> daysOfWeek,
        @NotNull LocalTime startTime,
        @NotNull LocalTime endTime,
        Long roomId,
        @NotBlank String sessionType,
        /**
         * Loại giáo viên (VIETNAMESE/FOREIGN) — bắt buộc, dùng chung cho
         * cả lô buổi tạo trong lời gọi này. Giáo viên phụ trách được hệ
         * thống tự động suy ra từ giáo viên chính (PRIMARY) đang active
         * của lớp cùng loại này (bổ sung ngoài SDD gốc, xác nhận
         * 2026-08-13).
         */
        @NotBlank String teacherType
) {}
