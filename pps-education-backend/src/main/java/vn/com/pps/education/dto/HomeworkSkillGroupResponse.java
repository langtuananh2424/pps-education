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
        long questionCount
) {}
