package vn.com.pps.education.dto;

import jakarta.validation.constraints.NotBlank;

/** UC-28 A1: cập nhật kế hoạch đã lập — chỉ sửa nội dung/trạng thái, không đổi kỳ lập kế hoạch (planType/period cố định lúc tạo). */
public record UpdateTeachingPlanRequest(
        String summary,
        String objectives,
        @NotBlank String status,
        boolean visibleToPartner
) {}
