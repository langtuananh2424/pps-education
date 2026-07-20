package vn.com.pps.education.dto;

import jakarta.validation.constraints.NotBlank;

/** UC-26: sửa metadata hoặc publish/archive 1 bài luyện Nghe/Chép chính tả/Nói. */
public record UpdateListeningPracticeItemRequest(
        @NotBlank String title,
        String audioUrl,
        @NotBlank String scriptText,
        String difficulty,
        Integer displayOrder,
        @NotBlank String status
) {}
