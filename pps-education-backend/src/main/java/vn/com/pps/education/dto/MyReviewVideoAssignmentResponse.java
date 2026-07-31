package vn.com.pps.education.dto;

import java.time.OffsetDateTime;

/**
 * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-07-31 — học sinh
 * tự xem hạn nộp (dueAt) của (các) bộ Video Ôn tập đã được giao cho lớp
 * mình đang học ACTIVE. Trước đây chỉ có
 * ReviewVideoService.listAssignmentsForClass (yêu cầu requireAssignedTeacher,
 * chặn Học sinh gọi) — không có nguồn self-service nào đọc được dueAt, xem
 * Javadoc ReviewVideoService.listMyAssignments. Mirror AssignedExerciseResponse
 * (bài ngữ pháp).
 */
public record MyReviewVideoAssignmentResponse(
        Long assignmentId,
        Long reviewVideoSetId,
        String reviewVideoSetTitle,
        String videoType,
        Long classId,
        String className,
        OffsetDateTime availableFrom,
        OffsetDateTime dueAt
) {}
