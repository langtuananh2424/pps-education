package vn.com.pps.education.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;

/**
 * UC-48 A3: dời lịch 1 buổi đang SCHEDULED. Đảo ngược quyết định
 * 2026-08-13 (xác nhận lại 2026-08-19): newStartTime/newEndTime đổi sang
 * newPeriodNumbers; giáo viên chính/phụ/CM giữ nguyên từ buổi cũ (không
 * còn re-derive), sửa GV thì dùng PATCH .../assignment riêng.
 * newDayPart (V129, xác nhận 2026-08-20): buổi Sáng/Chiều/Tối của tiết mới.
 */
public record RescheduleClassSessionRequest(
        @NotNull LocalDate newSessionDate,
        @NotBlank String newDayPart,
        @NotEmpty List<Integer> newPeriodNumbers,
        Long newRoomId,
        String reason
) {}
