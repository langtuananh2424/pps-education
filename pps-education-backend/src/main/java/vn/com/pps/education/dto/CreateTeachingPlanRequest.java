package vn.com.pps.education.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/**
 * UC-28 Main Flow bước 1-3: chọn lớp + kỳ lập kế hoạch (tuần/năm), nhập
 * nội dung. weekNumber/weekStartDate/weekEndDate bắt buộc khi
 * planType=WEEKLY; academicYear bắt buộc khi planType=YEARLY (CHECK
 * chk_plan_period — validate lại ở Service).
 */
public record CreateTeachingPlanRequest(
        @NotNull Long classId,
        @NotBlank String planType,
        String academicYear,
        Integer weekNumber,
        LocalDate weekStartDate,
        LocalDate weekEndDate,
        String summary,
        String objectives,
        boolean visibleToPartner
) {}
