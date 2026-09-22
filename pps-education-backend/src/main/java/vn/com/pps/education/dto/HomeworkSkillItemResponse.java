package vn.com.pps.education.dto;

/**
 * 1 Bài lẻ trong Lô giao BTVN theo kỹ năng ({@code HomeworkSkillBatch} —
 * Ngữ pháp/Đọc hiểu/Viết) — dùng trong {@link HomeworkProgressResponse}
 * (Cổng phụ huynh) để xem % TỪNG bài trong lô, không chỉ % gộp cả lô (bổ
 * sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-22).
 */
public record HomeworkSkillItemResponse(
        Long exerciseAssignmentId,
        String title,
        String progress,
        Boolean passed
) {}
