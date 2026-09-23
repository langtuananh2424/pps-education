package vn.com.pps.education.common;

import vn.com.pps.education.domain.Curriculum;

import java.util.List;

/**
 * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-22 — cấu hình theo khối cho rubric Writing
 * "v3" (gói {@code bo-cham-writing-K6-K9} do người training bàn giao, đóng gói 22/09/2026). Mirror ĐÚNG
 * khối {@code GRADES} trong {@code tham-khao/index.html} (label/exam/rows/thinCap) — câu chữ lấy nguyên
 * văn để không lệch với 17 bài kiểm thử chấp nhận đã chạy trên hệ thống tham chiếu.
 *
 * Mã khối (g6/g7/g7b1/g8/g8b1/g9) khớp đúng {@code HUONG_DAN_TICH_HOP.md} mục 2 "Bảng khối" — dùng làm
 * khoá tra rubric/key-grammar và chọn model gọi 9Router. Khối 9 CAMBRIDGE không có rubric (chưa được bàn
 * giao) — {@link #forGradeTrack} trả {@code null}, caller rơi về hàng chờ chấm tay như mọi trường hợp
 * thiếu rubric khác (xem {@link vn.com.pps.education.service.RubricByGradeTrackLoader}).
 */
public record WritingV3Grade(String code, String label, String exam, List<String> rows, String contentCriterion,
                              int thinContentCapPercent, int defaultWordLimit, boolean openingSentenceExempt) {

    public static final WritingV3Grade G6 = new WritingV3Grade("g6", "Lớp 6",
            "KET Writing — Part 6 (email/note) & Part 7 (story)",
            List.of("Content", "Organisation", "Language"), "Content", 60, 25, true);

    public static final WritingV3Grade G7_IELTS = new WritingV3Grade("g7", "Lớp 7",
            "KET–PET Writing — email, message, short story",
            List.of("Task Response / Achievement", "Coherence & Cohesion", "Lexical Resource", "Grammatical Range & Accuracy"),
            "Task Response / Achievement", 50, 60, true);

    public static final WritingV3Grade G7_CAMBRIDGE = new WritingV3Grade("g7b1", "Lớp 7 (hệ Cambridge)",
            "PET Foundation — email trả lời, tin nhắn, lời mời",
            List.of("Content", "Communicative Achievement", "Organisation", "Language"), "Content", 50, 60, true);

    public static final WritingV3Grade G8_IELTS = new WritingV3Grade("g8", "Lớp 8",
            "IELTS Writing Task 2 (rút gọn, 100 từ)",
            List.of("Task Response / Achievement", "Coherence & Cohesion", "Lexical Resource", "Grammatical Range & Accuracy"),
            "Task Response / Achievement", 40, 100, false);

    public static final WritingV3Grade G8_CAMBRIDGE = new WritingV3Grade("g8b1", "Lớp 8 (hệ Cambridge)",
            "PET Writing — Part 1 (email) & Part 2 (article / story)",
            List.of("Content", "Communicative Achievement", "Organisation", "Language"), "Content", 40, 100, false);

    public static final WritingV3Grade G9_IELTS = new WritingV3Grade("g9", "Lớp 9",
            "IELTS Writing Task 2 (250 từ)",
            List.of("Task Response / Achievement", "Coherence & Cohesion", "Lexical Resource", "Grammatical Range & Accuracy"),
            "Task Response / Achievement", 40, 250, false);

    /**
     * @return {@code null} nếu chưa xác định được (thiếu gradeLevel/track) hoặc tổ hợp chưa có rubric
     *         (Khối 9 Cambridge) — caller coi như "chưa chấm được", mirror
     *         {@link vn.com.pps.education.service.RubricByGradeTrackLoader#load}.
     */
    public static WritingV3Grade forGradeTrack(Curriculum.GradeLevel gradeLevel, Curriculum.Track track) {
        if (gradeLevel == null) {
            return null;
        }
        if (gradeLevel == Curriculum.GradeLevel.GRADE_6) {
            return G6;
        }
        if (track == null) {
            return null;
        }
        return switch (gradeLevel) {
            case GRADE_7 -> track == Curriculum.Track.IELTS ? G7_IELTS : G7_CAMBRIDGE;
            case GRADE_8 -> track == Curriculum.Track.IELTS ? G8_IELTS : G8_CAMBRIDGE;
            case GRADE_9 -> track == Curriculum.Track.IELTS ? G9_IELTS : null;
            default -> null;
        };
    }
}
