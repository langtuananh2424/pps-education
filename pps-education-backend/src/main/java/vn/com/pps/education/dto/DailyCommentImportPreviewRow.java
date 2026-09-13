package vn.com.pps.education.dto;

/**
 * 1 dòng đã parse từ file Excel BTVN — dùng cho preview (chưa ghi DB), xem
 * Javadoc {@code StudentCommentService#previewImportComments} (bổ sung
 * ngoài SDD gốc, đã xác nhận với người dùng 2026-08-14).
 *
 * Bổ sung 2026-09-12 (đã xác nhận với người dùng) — bỏ hẳn 4 field BTVN
 * online (homeworkNextExerciseId/homeworkNextReviewVideoSetId/
 * homeworkNextReadingExerciseId/homeworkNextWritingExerciseId): Excel từ
 * nay CHỈ phục vụ Nhận xét (content/thái độ/BTVN offline) — giao BTVN
 * online chỉ còn làm được qua "Áp dụng cho cả lớp" trên web, xem Javadoc
 * {@code StudentCommentService#applyHomeworkToClass}.
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
        String note
) {}
