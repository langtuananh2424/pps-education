package vn.com.pps.education.dto;

import java.util.List;

/**
 * V150 — 1 nhóm kỹ năng khả dụng làm nguồn "BTVN buổi sau" ở UC-21 (1 Lesson có >=1 Bài PUBLISHED cùng
 * skillCategory).
 *
 * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-23 — trước đây chọn 1 nhóm là giao TOÀN BỘ
 * exerciseCount Bài trong đó cùng lúc, không chọn lọc được (xem
 * HomeworkSkillBatchService#assignBatchToClass V150 cũ). Giờ thêm {@code exercises} để FE hiện checklist
 * cho GV bỏ bớt Bài không muốn giao — {@code exerciseCount}/{@code questionCount} vẫn giữ nguyên (tổng
 * của CẢ nhóm, dùng cho nhãn dropdown khi CHƯA mở checklist).
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
        String subTopicTitle,
        /** Bổ sung 2026-09-23 — từng Bài PUBLISHED trong nhóm, để FE hiện checklist chọn lọc. */
        List<ExerciseSummary> exercises
) {
    /** 1 dòng checklist — chỉ đủ field để FE hiện nhãn "Mã bài — Tên bài (N câu)" và tick chọn. */
    public record ExerciseSummary(Long id, String code, String title, long questionCount) {}
}
