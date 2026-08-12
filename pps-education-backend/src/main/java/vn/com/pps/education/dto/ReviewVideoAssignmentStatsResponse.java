package vn.com.pps.education.dto;

import vn.com.pps.education.domain.ReviewVideoAssignment;
import vn.com.pps.education.domain.ReviewVideoSet;

import java.time.OffsetDateTime;

/**
 * UC-66 bổ sung ngoài SDD gốc (đã xác nhận với người dùng 2026-08-11) — 1 dòng "BTVN Video Ôn tập"
 * (REFLEX/CONNECTION) cho thống kê theo lớp, mirror {@code ExerciseAssignmentStatsResponse} nhưng KHÔNG
 * có passedCount/passRatePercent — Video Ôn tập chưa có khái niệm ngưỡng điểm đạt/rớt nào trong schema
 * (khác Exercise có pass_threshold_percent), FE hiển thị "—" cho cột % đạt thay vì bịa ra 1 con số.
 */
public record ReviewVideoAssignmentStatsResponse(
        Long assignmentId,
        Long reviewVideoSetId,
        String reviewVideoSetCode,
        String reviewVideoSetTitle,
        ReviewVideoSet.VideoType videoType,
        /** Bổ sung ngoài SDD gốc (đã xác nhận với người dùng 2026-08-11) — dùng lọc GV Việt Nam/nước ngoài ở UC-66. */
        ReviewVideoSet.TeacherType teacherType,
        OffsetDateTime availableFrom,
        OffsetDateTime dueAt,
        ReviewVideoAssignment.Status status,
        int totalStudents,
        int completedCount,
        int completionPercent,
        /**
         * Bổ sung ngoài SDD gốc (đã xác nhận với người dùng 2026-08-11) — CHỈ có giá trị cho bộ
         * CONNECTION (điểm trắc nghiệm tổng ≥ ngưỡng % pass của video, xem ReviewVideoService).
         * NULL cho bộ REFLEX — chưa có khái niệm điểm/ngưỡng đạt nào, FE hiển thị "—".
         */
        Integer passedCount,
        Integer passRatePercent
) {}
