package vn.com.pps.education.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * Sửa nhanh tại chỗ 1 buổi đang SCHEDULED (bổ sung ngoài SDD gốc, xác
 * nhận 2026-08-19, phục vụ click-thẻ trên lưới thời khóa biểu) — sửa
 * phòng/loại GV/GV chính-phụ-CM/tiết CÙNG ngày, không đổi sessionDate
 * (đổi ngày phải đi qua reschedule — UC-48 A3 — để giữ đúng ngữ nghĩa
 * RESCHEDULED + audit trail). dayPart (V129, xác nhận 2026-08-20): buổi
 * Sáng/Chiều/Tối của tiết mới chọn.
 */
public record UpdateSessionAssignmentRequest(
        Long roomId,
        @NotBlank String teacherType,
        @NotNull Long primaryTeacherId,
        Long assistantTeacherId,
        Long cmTeacherId,
        @NotBlank String dayPart,
        @NotEmpty List<Integer> periodNumbers
) {}
