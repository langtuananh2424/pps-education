package vn.com.pps.education.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** UC-26: GV soạn 1 bài luyện Nghe/Chép chính tả/Nói, gắn theo curriculum. */
public record CreateListeningPracticeItemRequest(
        @NotNull Long curriculumId,
        @NotBlank String title,
        @NotBlank String mode,
        String audioUrl,
        @NotBlank String scriptText,
        String difficulty,
        Integer displayOrder
) {}
