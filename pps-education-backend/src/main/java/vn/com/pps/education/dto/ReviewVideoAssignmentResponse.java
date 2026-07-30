package vn.com.pps.education.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * V65 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-07-30) —
 * mirror {@code ExerciseAssignmentResponse} cho bản giao Video Ôn tập
 * (bảng {@code review_video_assignments}, mới từ V65). Dùng để FE tra
 * ngược "bản giao này ứng với ReviewVideoSet nào" khi tải lại 1 comment
 * DAILY đã có sẵn (StudentCommentResponse chỉ trả
 * homeworkNextReviewVideoAssignmentId + title, không trả thẳng
 * reviewVideoSetId).
 */
public record ReviewVideoAssignmentResponse(
        Long id,
        UUID uuid,
        Long reviewVideoSetId,
        String reviewVideoSetTitle,
        Long classId,
        Long assignedBy,
        OffsetDateTime availableFrom,
        OffsetDateTime dueAt,
        List<Long> targetStudentIds,
        String status
) {}
