package vn.com.pps.education.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Map;

public record StudentCommentResponse(
        Long id,
        Long studentId,
        String studentFullName,
        Long classId,
        Long teacherId,
        String commentType,
        Long classSessionId,
        Long gradePeriodId,
        LocalDate commentDate,
        String content,
        Map<String, Object> structuredContent,
        String severity,
        boolean isWarning,
        String status,
        OffsetDateTime submittedAt,
        OffsetDateTime approvedAt,
        Long approvedBy,
        OffsetDateTime visibleToParentAt,
        String rejectionReason,
        // Nhận xét Hàng ngày kiểu mới (chỉ có ý nghĩa khi commentType=DAILY) —
        // bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-07-24.
        String attitude,
        String homeworkPreviousScore,
        String homeworkPreviousSpeakingScore,
        String homeworkNext,
        Long homeworkNextExerciseAssignmentId,
        String homeworkNextExerciseTitle,
        Long homeworkNextReviewVideoSetId,
        String homeworkNextReviewVideoSetTitle,
        String grammarPreviousProgress,
        String videoPreviousProgress,
        String note,
        /** "Bài học hôm nay" của buổi (class_sessions.lesson_content) — null nếu không phải DAILY. Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-07-29. */
        String lessonContent
) {}
