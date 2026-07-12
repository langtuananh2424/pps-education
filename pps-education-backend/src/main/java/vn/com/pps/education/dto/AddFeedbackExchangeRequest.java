package vn.com.pps.education.dto;

import jakarta.validation.constraints.NotBlank;

/** UC-39 A1: 1 lượt trao đổi qua lại giữa Quản lý điểm trường và Đại diện trường liên kết trước khi giải quyết. */
public record AddFeedbackExchangeRequest(
        @NotBlank String note
) {}
