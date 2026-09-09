package vn.com.pps.education.dto;

/**
 * V150 — 1 nhóm kỹ năng khả dụng làm nguồn "BTVN buổi sau" ở UC-21 (1 Lesson có >=1 Bài PUBLISHED cùng
 * skillCategory). Chọn 1 nhóm này = giao TOÀN BỘ exerciseCount Bài trong đó cùng lúc (xem
 * HomeworkSkillBatchService#assignBatchToClass) — value gửi lên là (examId, skillCategory), không còn
 * là 1 exerciseId đơn.
 */
public record HomeworkSkillGroupResponse(
        Long examId,
        String examCode,
        String examTitle,
        String examTeacherType,
        String skillCategory,
        int exerciseCount,
        long questionCount,
        /**
         * Bổ sung 2026-09-04 (đã xác nhận với người dùng) — tên Unit/SubTopic chứa Lesson này — Lesson
         * đánh số lặp lại (Lesson 1, 2, 3...) giữa nhiều Unit/SubTopic khác nhau, dropdown "BTVN buổi
         * sau" (UC-21) chỉ hiện examTitle rất dễ giao NHẦM Lesson. NULL khi Exam chưa phân loại.
         */
        String unitTitle,
        String subTopicTitle
) {}
