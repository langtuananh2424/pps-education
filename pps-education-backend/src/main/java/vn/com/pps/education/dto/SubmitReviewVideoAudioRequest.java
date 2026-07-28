package vn.com.pps.education.dto;

import jakarta.validation.constraints.NotBlank;

/** UC-23b: Học sinh nộp/nộp lại audio trả lời cho video REFLEX — audioUrl đã upload sẵn qua POST /api/media/upload. */
public record SubmitReviewVideoAudioRequest(
        @NotBlank String audioUrl
) {}
