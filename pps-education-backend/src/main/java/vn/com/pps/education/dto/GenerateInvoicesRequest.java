package vn.com.pps.education.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/**
 * UC-30 Main Flow bước 1: sinh hóa đơn định kỳ. classId để trống = áp
 * dụng cho toàn bộ lớp đang có tuition_plan_assignments active (dùng cho
 * cron tự động); truyền classId cụ thể khi Kế toán muốn sinh bổ sung/sinh
 * lại cho 1 lớp.
 */
public record GenerateInvoicesRequest(
        Long classId,
        @NotNull LocalDate billingPeriodFrom,
        @NotNull LocalDate billingPeriodTo,
        @NotNull LocalDate issueDate,
        @NotNull LocalDate dueDate
) {}
