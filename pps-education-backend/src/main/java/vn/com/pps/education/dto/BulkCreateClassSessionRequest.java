package vn.com.pps.education.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;

/**
 * UC-56: Sinh lịch học hàng loạt theo mẫu lặp (FR-ACA-05, bổ sung ngoài
 * SDD gốc, đã xác nhận với người dùng). daysOfWeek dùng đúng tên hằng số
 * java.time.DayOfWeek (MONDAY..SUNDAY). Với mỗi ngày trong [startDate,
 * endDate] khớp daysOfWeek, hệ thống thử tạo 1 buổi — xem Javadoc
 * ClassSessionService.bulkCreateSessions. Đảo ngược quyết định 2026-08-13
 * (xác nhận lại 2026-08-19) — xem Javadoc CreateClassSessionRequest.
 * dayPart (V129, xác nhận 2026-08-20): buổi Sáng/Chiều/Tối, dùng chung
 * cho cả lô.
 */
public record BulkCreateClassSessionRequest(
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate,
        @NotEmpty List<String> daysOfWeek,
        @NotBlank String dayPart,
        @NotEmpty List<Integer> periodNumbers,
        Long roomId,
        @NotBlank String sessionType,
        @NotBlank String teacherType,
        @NotNull Long primaryTeacherId,
        Long assistantTeacherId,
        Long cmTeacherId
) {}
