package vn.com.pps.education.common;

/**
 * V200 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-29) — UC-23b: dạng đề của 1 câu hỏi Video phản xạ,
 * giáo viên chọn khi soạn câu hỏi. Tên hằng trùng tên cột ngưỡng của rubric v3 ({@code rubricFormat} của
 * {@link ReflexV2Task}). Dạng nào hợp lệ với khối/tuyến nào: {@link ReflexV2Task#allowedFormats}.
 */
public enum ReflexQuestionFormat {
    /** Câu hỏi ngắn (trả lời 20-30 giây). */
    SHORT,
    /** IELTS Speaking Part 2 — cue card. */
    PART2,
    /** Cambridge PET Speaking Part 4 — câu hỏi thảo luận. */
    PET4,
    /** Cambridge PET Speaking Task 2 — tả tranh; bắt buộc có mô tả tranh bằng chữ để xét lạc đề. */
    PICTURE
}
