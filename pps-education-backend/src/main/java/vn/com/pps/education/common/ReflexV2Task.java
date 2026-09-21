package vn.com.pps.education.common;

import vn.com.pps.education.domain.Curriculum;

import java.util.List;
import java.util.Optional;

/**
 * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-21 — cấu hình từng dạng bài của bộ tiêu chí
 * Speaking v2 (Khối 6-9; dạng miêu tả ảnh PICTURE của Khối 7 Cambridge CHƯA làm ở đợt này) do người
 * training bàn giao — mirror {@code tasks.js} trong gói {@code ma-nguon-tham-chieu/}. Mỗi dạng bài chọn
 * theo (Khối, chương trình) của Curriculum, cùng nguyên tắc {@code RubricByGradeTrackLoader}: tổ hợp chưa
 * có bộ v2 (Khối 9 Cambridge, Khối 7-9 thiếu track) trả {@link Optional#empty()} — caller dùng luồng cũ.
 *
 * <b>Dạng đề SHORT / PART2 (Khối 8-9 IELTS):</b> hai dạng dùng CHUNG một cặp file rubric, chỉ khác cột
 * ngưỡng, và người training yêu cầu BẮT BUỘC truyền cột đúng — thiếu thì AI mặc định cột SHORT và chấm sai
 * toàn bộ checkpoint dạng đếm của bài 90 giây ({@code HANDOFF-grade8.md} §1). Hệ thống chưa có trường
 * "dạng đề" trên câu hỏi, nên SUY RA từ thời lượng ghi âm tối đa của câu hỏi — người training quy định cố
 * định SHORT = 30 giây, PART2 = 90 giây: từ {@link #PART2_MIN_SECONDS} giây trở lên là PART2. Giả định này
 * cần người dùng xác nhận; nếu sau này thêm cột "dạng đề" thì thay đúng chỗ {@link #forGradeTrack}.
 *
 * @param criteria        các tiêu chí của bài NÓI, theo thứ tự (gồm cả tiêu chí ngữ pháp đã khoá từ Bước 1).
 * @param writingCriteria các tiêu chí chấm ở Bước 1 (bài viết).
 * @param grammarCode     tiêu chí Ngữ pháp — chấm ở Bước 1 rồi KHOÁ, Bước 2 không chấm lại.
 * @param seconds         thời lượng ghi âm tối đa mà mọi ngưỡng của rubric được hiệu chuẩn theo.
 * @param minWords        ngưỡng số từ của cổng "quá ngắn/không đủ dữ liệu" (dưới ngưỡng → trần 40%).
 * @param gateScheme      ý nghĩa của mã cổng C1/C2/C3 — xem {@link GateScheme}.
 * @param rubricFormat    {@code "SHORT"}/{@code "PART2"} cho Khối 8-9 IELTS (bắt buộc truyền vào prompt); null nếu rubric không có cột theo dạng đề.
 */
public record ReflexV2Task(String id, List<String> criteria, List<String> writingCriteria, String grammarCode,
                           int seconds, int minWords, GateScheme gateScheme, String rubricFormat,
                           String levelLabel, String formatLabel, String writingRubricFile, String speakingRubricFile) {

    /**
     * STANDARD (Khối 6, 7, 8 Cambridge): C1 = lạc đề, C2 = không nghe ra (chặn Phát âm), C3 = quá ngắn.
     * V3 (Khối 8-9 IELTS): C1 = không đủ dữ liệu (quá ngắn), C2 = lạc đề — KHÔNG có cổng "không nghe ra".
     * Cùng mã C1 nhưng hai nghĩa khác nhau, nên lời giải thích cổng chặn phải theo kiểu này.
     */
    public enum GateScheme { STANDARD, V3 }

    /** Ghi âm tối đa từ ngưỡng này trở lên coi là PART2 (SHORT = 30 giây, PART2 = 90 giây theo người training). */
    public static final int PART2_MIN_SECONDS = 60;

    /** Tiêu chí P (Phát âm) luôn là tiêu chí chấm ở Bước 2 — chưa được kiểm chứng đủ, xem {@code ReflexV2AiGradingService}. */
    public static final String PRONUNCIATION_CODE = "P";

    private static final List<String> IELTS_SPEAKING = List.of("FC", "LR", "GRA", "P");
    private static final List<String> IELTS_WRITING = List.of("LR", "GRA");
    private static final List<String> CAMBRIDGE_SPEAKING = List.of("GV", "DM", "P");
    private static final List<String> CAMBRIDGE_WRITING = List.of("GV");

    private static final ReflexV2Task GRADE_6 = new ReflexV2Task(
            "g6-short", List.of("GV", "P"), CAMBRIDGE_WRITING, "GV", 20, 8, GateScheme.STANDARD, null,
            "CEFR A1–A2, học sinh 11–12 tuổi", "Short question",
            "rubric-grade6-writing.md", "rubric-grade6-speaking.md");

    private static final ReflexV2Task GRADE_7_IELTS = new ReflexV2Task(
            "g7-ielts-short", IELTS_SPEAKING, IELTS_WRITING, "GRA", 25, 15, GateScheme.STANDARD, null,
            "CEFR A2–B1 (IELTS 3.5–4.0), học sinh 12–13 tuổi", "Short question",
            "rubric-grade7-ielts-writing.md", "rubric-grade7-ielts-speaking.md");

    private static final ReflexV2Task GRADE_7_CAMBRIDGE = new ReflexV2Task(
            "g7-cam-short", CAMBRIDGE_SPEAKING, CAMBRIDGE_WRITING, "GV", 25, 15, GateScheme.STANDARD, null,
            "CEFR A2–B1, học sinh 12–13 tuổi", "Short question",
            "rubric-grade7-cambridge-writing.md", "rubric-grade7-cambridge-speaking.md");

    private static final ReflexV2Task GRADE_8_IELTS_SHORT = new ReflexV2Task(
            "g8-ielts-short", IELTS_SPEAKING, IELTS_WRITING, "GRA", 30, 15, GateScheme.V3, "SHORT",
            "IELTS 4.0 foundation, học sinh 13–14 tuổi", "Short question",
            "rubric-grade8-ielts-writing.md", "rubric-grade8-ielts-speaking.md");

    private static final ReflexV2Task GRADE_8_IELTS_PART2 = new ReflexV2Task(
            "g8-ielts-part2", IELTS_SPEAKING, IELTS_WRITING, "GRA", 90, 30, GateScheme.V3, "PART2",
            "IELTS 4.0 foundation, học sinh 13–14 tuổi", "IELTS Speaking Part 2",
            "rubric-grade8-ielts-writing.md", "rubric-grade8-ielts-speaking.md");

    private static final ReflexV2Task GRADE_8_CAMBRIDGE = new ReflexV2Task(
            "g8-cam-pet4", CAMBRIDGE_SPEAKING, CAMBRIDGE_WRITING, "GV", 60, 30, GateScheme.STANDARD, null,
            "IELTS 4.0 (≈ CEFR A2+/B1), học sinh 13–14 tuổi", "PET Speaking Part 4",
            "rubric-grade8-cambridge-writing.md", "rubric-grade8-cambridge-speaking.md");

    private static final ReflexV2Task GRADE_9_IELTS_SHORT = new ReflexV2Task(
            "g9-ielts-short", IELTS_SPEAKING, IELTS_WRITING, "GRA", 30, 18, GateScheme.V3, "SHORT",
            "IELTS 4.0–5.0 foundation, học sinh 14–15 tuổi", "Short question",
            "rubric-grade9-ielts-writing.md", "rubric-grade9-ielts-speaking.md");

    private static final ReflexV2Task GRADE_9_IELTS_PART2 = new ReflexV2Task(
            "g9-ielts-part2", IELTS_SPEAKING, IELTS_WRITING, "GRA", 90, 35, GateScheme.V3, "PART2",
            "IELTS 4.0–5.0 foundation, học sinh 14–15 tuổi", "IELTS Speaking Part 2",
            "rubric-grade9-ielts-writing.md", "rubric-grade9-ielts-speaking.md");

    /**
     * @param maxRecordingSeconds thời lượng ghi âm tối đa của câu hỏi — CHỈ dùng để phân biệt SHORT/PART2 ở
     *                            Khối 8-9 IELTS (xem Javadoc lớp); các dạng còn lại bỏ qua.
     */
    public static Optional<ReflexV2Task> forGradeTrack(Curriculum.GradeLevel gradeLevel, Curriculum.Track track,
                                                       int maxRecordingSeconds) {
        if (gradeLevel == null) {
            return Optional.empty();
        }
        boolean part2 = maxRecordingSeconds >= PART2_MIN_SECONDS;
        switch (gradeLevel) {
            case GRADE_6:
                return Optional.of(GRADE_6);
            case GRADE_7:
                if (track == Curriculum.Track.IELTS) {
                    return Optional.of(GRADE_7_IELTS);
                }
                return track == Curriculum.Track.CAMBRIDGE ? Optional.of(GRADE_7_CAMBRIDGE) : Optional.empty();
            case GRADE_8:
                if (track == Curriculum.Track.IELTS) {
                    return Optional.of(part2 ? GRADE_8_IELTS_PART2 : GRADE_8_IELTS_SHORT);
                }
                return track == Curriculum.Track.CAMBRIDGE ? Optional.of(GRADE_8_CAMBRIDGE) : Optional.empty();
            case GRADE_9:
                // Khối 9 CAMBRIDGE: người training chưa bàn giao bộ tiêu chí → luồng cũ.
                return track == Curriculum.Track.IELTS
                        ? Optional.of(part2 ? GRADE_9_IELTS_PART2 : GRADE_9_IELTS_SHORT) : Optional.empty();
            default:
                return Optional.empty();
        }
    }
}
