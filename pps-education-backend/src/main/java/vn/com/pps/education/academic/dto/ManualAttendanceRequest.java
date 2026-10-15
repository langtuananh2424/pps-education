package vn.com.pps.education.academic.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * UC-09 — bổ sung ngoài Main Flow gốc: quản trị viên/HR nhập chấm công thủ công thay cho nhân sự
 * (quên chấm, lỗi thiết bị...). Cần ít nhất 1 trong checkInTime/checkOutTime; reason bắt buộc để đối soát.
 */
public record ManualAttendanceRequest(
        @NotNull Long employeeId,
        @NotNull LocalDate workDate,
        LocalTime checkInTime,
        LocalTime checkOutTime,
        Long siteId,
        @NotBlank @Size(max = 500) String reason
) {}
