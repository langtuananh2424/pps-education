package vn.com.pps.education.dto;

import jakarta.validation.constraints.NotBlank;

/** UC-38 Main Flow bước 1-3: Đại diện trường liên kết gửi phản hồi tới Quản lý điểm trường phụ trách. */
public record SubmitPartnerFeedbackRequest(
        @NotBlank String feedbackType,
        @NotBlank String content,
        String priority
) {}
