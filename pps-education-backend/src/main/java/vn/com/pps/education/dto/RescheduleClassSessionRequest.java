package vn.com.pps.education.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * UC-48 Alternate Flow A3: Dời lịch buổi. Giáo viên phụ trách buổi mới
 * được hệ thống tự động suy ra lại từ giáo viên chính (PRIMARY) đang
 * active của lớp theo đúng loại giáo viên (VIETNAMESE/FOREIGN) của buổi
 * cũ — không nhập tay (bổ sung ngoài SDD gốc, xác nhận 2026-08-13).
 */
public record RescheduleClassSessionRequest(
        @NotNull LocalDate newSessionDate,
        @NotNull LocalTime newStartTime,
        @NotNull LocalTime newEndTime,
        Long newRoomId,
        String reason
) {}
