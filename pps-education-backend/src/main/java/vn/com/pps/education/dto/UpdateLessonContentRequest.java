package vn.com.pps.education.dto;

import jakarta.validation.constraints.NotBlank;

/** "Bài học hôm nay" (bổ sung ngoài SDD gốc, đã xác nhận với người dùng). */
public record UpdateLessonContentRequest(@NotBlank String lessonContent) {
}
