package vn.com.pps.education.dto;

/**
 * 1 dòng đã parse từ file Excel BTVN — dùng cho preview (chưa ghi DB), xem
 * Javadoc {@code StudentCommentService#previewImportComments} (bổ sung
 * ngoài SDD gốc, đã xác nhận với người dùng 2026-08-14). Trả thẳng id
 * NGUỒN (Exercise/ReviewVideoSet) — khác {@link StudentCommentResponse}
 * (trả id BẢN GIAO) vì chưa có bản giao nào được tạo ở bước preview này.
 */
public record DailyCommentImportPreviewRow(
        Long studentId,
        String attitude,
        String homeworkPreviousScore,
        String homeworkPreviousSpeakingScore,
        /** V130 — chỉ khác null khi buổi teacherType=VIETNAMESE (xem HomeworkColumns trong StudentCommentService). */
        String homeworkPreviousReadingScore,
        String homeworkPreviousWritingScore,
        String content,
        String homeworkNext,
        String homeworkNextReading,
        String homeworkNextWriting,
        Long homeworkNextExerciseId,
        Long homeworkNextReviewVideoSetId,
        /** V137 — chỉ khác null khi buổi teacherType=VIETNAMESE. */
        Long homeworkNextReadingExerciseId,
        Long homeworkNextWritingExerciseId,
        String note
) {}
