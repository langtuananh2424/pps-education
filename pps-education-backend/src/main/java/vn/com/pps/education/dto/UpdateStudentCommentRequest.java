package vn.com.pps.education.dto;

import java.util.Map;

/**
 * UC-21 Main Flow bước 2, A1 (sửa lại sau khi bị từ chối) — chỉ sửa nội
 * dung, không đổi liên kết ngữ cảnh (classSessionId).
 *
 * Bổ sung 2026-09-12 (đã xác nhận với người dùng) — bỏ hẳn 6 field BTVN
 * online, mirror {@link CreateStudentCommentRequest} — xem Javadoc đó để
 * biết lý do (giao BTVN online tách hẳn khỏi Viết/Sửa nhận xét, chỉ còn
 * qua {@code StudentCommentService#applyHomeworkToClass}).
 */
public record UpdateStudentCommentRequest(
        // Bổ sung ngoài SDD gốc, xác nhận 2026-08-17 — bỏ @NotBlank, xem Javadoc CreateStudentCommentRequest.content.
        String content,
        Map<String, Object> structuredContent,
        String severity,
        boolean isWarning,
        String attitude,
        String homeworkPreviousScore,
        String homeworkPreviousSpeakingScore,
        // V130 — mirror CreateStudentCommentRequest, chỉ có ý nghĩa khi buổi teacherType=VIETNAMESE.
        String homeworkPreviousReadingScore,
        String homeworkPreviousWritingScore,
        String homeworkNext,
        String homeworkNextReading,
        String homeworkNextWriting,
        String note
) {}
