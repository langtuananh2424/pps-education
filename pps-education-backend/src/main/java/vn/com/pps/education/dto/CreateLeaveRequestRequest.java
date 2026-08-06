package vn.com.pps.education.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * UC-10 Main Flow bước 2: nộp đơn từ. startTime/endTime chỉ áp dụng
 * LEAVE_TYPE=LATE/EARLY_LEAVE. substitutes: UC-10 bước 3 (chỉ Giáo viên
 * có buổi dạy trong khoảng nghỉ mới điền, null/rỗng với nhân sự khác hoặc
 * A4 — không có lịch dạy).
 */
public record CreateLeaveRequestRequest(
        @NotBlank String leaveType,
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate,
        LocalTime startTime,
        LocalTime endTime,
        @NotBlank String reason,
        String attachmentUrl,
        List<SubstituteAssignmentRequest> substitutes
) {}
