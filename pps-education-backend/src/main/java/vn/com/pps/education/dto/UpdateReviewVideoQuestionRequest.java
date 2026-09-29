package vn.com.pps.education.dto;

import jakarta.validation.constraints.NotNull;

/** Bổ sung ngoài SDD gốc (đã xác nhận với người dùng 2026-08-12) — Giáo viên sửa 1 câu hỏi gắn mốc thời gian đã có của video REFLEX (trước đây chỉ thêm mới được, không sửa được). Mirror AddReviewVideoQuestionRequest. */
public record UpdateReviewVideoQuestionRequest(
        @NotNull Integer timestampSeconds,
        String prompt,
        @NotNull Integer maxRecordingSeconds,
        Integer maxAttempts,
        Integer displayOrder,
        /**
         * V200 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-29) — dạng đề: SHORT / PART2 / PET4 /
         * PICTURE; null = để hệ thống suy theo thời lượng như câu hỏi cũ. Dạng hợp lệ theo chương trình:
         * GET /api/reflex-question-formats.
         */
        String questionFormat,
        /** V200 — dạng PICTURE: ảnh tranh đã lưu trên hệ thống (khung hình chụp từ video hoặc giáo viên tải lên). */
        String pictureImageUrl,
        /** V200 — dạng PICTURE (bắt buộc): 2-3 dòng mô tả tranh giáo viên đã duyệt, chỉ dùng để AI xét lạc đề. */
        String pictureBrief
) {
    /** Giữ chữ ký cũ cho nơi chưa có dạng đề (import Excel, test) — dạng đề null = suy theo thời lượng. */
    public UpdateReviewVideoQuestionRequest(Integer timestampSeconds, String prompt, Integer maxRecordingSeconds, Integer maxAttempts,
            Integer displayOrder) {
        this(timestampSeconds, prompt, maxRecordingSeconds, maxAttempts, displayOrder, null, null, null);
    }
}
